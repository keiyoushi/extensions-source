package eu.kanade.tachiyomi.extension.en.dreammanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebViewBlocking
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.time.Clock
import kotlin.time.Instant

@Source
abstract class DreamManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { passGuard(it) }
        .rateLimit(3)

    override suspend fun getPopularManga(page: Int) = getList("/comix/", page, "news_read", "desc", "cat_1")

    override suspend fun getLatestUpdates(page: Int) = getList("/comix/", page, "editdate", "desc", "cat_1")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/index.php".toHttpUrl().newBuilder()
                .addQueryParameter("do", "search")
                .addQueryParameter("subaction", "search")
                .addQueryParameter("story", query.trim())
                .apply { if (page > 1) addQueryParameter("search_start", page.toString()) }
                .build()
            val document = client.get(url, Fetch.DOCUMENT).asJsoup()
            return MangasPage(document.parseCards(), document.selectFirst("a[onclick*=list_submit(${page + 1})]") != null)
        }

        val sort = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()
        val genres = filters.firstInstanceOrNull<GenreFilter>()
        val years = filters.filterIsInstance<YearFilter>().map { it.year }
        val segments = buildList {
            genres?.included?.ifEmpty { null }?.let { add("g=${it.joinToString(",")}") }
            genres?.excluded?.ifEmpty { null }?.let { add("exc_g=${it.joinToString(",")}") }
            years.getOrNull(0)?.let { add("y[from]=$it") }
            years.getOrNull(1)?.let { add("y[to]=$it") }
        }
        // The filter page without filters redirects to the catalogue, which has its own sort form
        return if (segments.isEmpty()) {
            getList("/comix/", page, sort.value, sort.direction, "cat_1")
        } else {
            getList("/ComicList/${segments.joinToString("/")}/", page, sort.value, sort.direction, "xfilter")
        }
    }

    // A list is sorted for the request that posts the site's sort form, as on the site
    private suspend fun getList(path: String, page: Int, sortBy: String, direction: String, sortName: String): MangasPage {
        val url = baseUrl + path + if (page > 1) "page/$page/" else ""
        val body = FormBody.Builder()
            .add("dlenewssortby", sortBy)
            .add("dledirection", direction)
            .add("set_new_sort", "dle_sort_$sortName")
            .add("set_direction_sort", "dle_direction_$sortName")
            .build()
        val document = client.post(url, Fetch.DOCUMENT, body).asJsoup()
        val hasNextPage = document.selectFirst("div.pagination__pages")?.children()?.lastOrNull()?.tagName() == "a"
        return MangasPage(document.parseCards(), hasNextPage)
    }

    private fun Document.parseCards() = select("#dle-content .readed").map { card ->
        SManga.create().apply {
            val link = card.selectFirst(".readed__title > a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.text()
            thumbnail_url = card.selectFirst("img")?.let { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val path = when {
            MANGA_PATH.matches(url.encodedPath) -> url.encodedPath
            url.pathSegments.firstOrNull() == "reader" -> client.get(url, Fetch.DOCUMENT).asJsoup().dataScript().parseAs<ReaderDto>()
                .mangaLink?.toHttpUrlOrNull()?.encodedPath
            else -> null
        } ?: return null
        return client.get(baseUrl + path, Fetch.DOCUMENT).asJsoup().parseDetails(path)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()
        val data = document.dataScript().parseAs<MangaDataDto>()
        return SMangaUpdate(document.parseDetails(manga.url), data.chapters.map { it.toSChapter(data.newsId) })
    }

    private fun Document.parseDetails(path: String) = SManga.create().apply {
        url = path
        title = selectFirst("header.page__header h1")!!.text()
        thumbnail_url = selectFirst("div.page__poster img")?.absUrl("src")
        genre = select("div.page__tags a").eachText().joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(selectFirst("div.page__text"))
            .altTitles(selectFirst("header.page__header h2")?.text()?.split(" ; ").orEmpty())
            .build()
        status = when (selectFirst(".page__list > li:has(> div:contains(Release type))")?.ownText()?.lowercase()) {
            null -> SManga.UNKNOWN
            "ongoing" -> SManga.ONGOING
            "completed", "finished" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(baseUrl + chapter.url, Fetch.DOCUMENT).asJsoup().dataScript().parseAs<ReaderDto>()
        // Adult manga only open for logged-in members: guests get a warning page, even after confirming their age
        if (data.images.isEmpty() && data.age != null) {
            throw Exception("This manga is for adults: log in through the WebView to read it")
        }
        // The reader shows the first `pages` images
        return data.images.take(data.pages ?: data.images.size).mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/comix/", Fetch.DOCUMENT).asJsoup()
        .selectFirst("script:containsData(window.__XFILTER__)")!!.data()
        .substringAfter("window.__XFILTER__ = ").substringBeforeLast(";")
        .parseAs<XFilterDto>().filterItems.g.values.toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(data?.parseAs<List<GenreDto>>())

    private fun Document.dataScript(): String = selectFirst("script:containsData(window.__DATA__)")!!.data()
        .substringAfter("window.__DATA__ = ").substringBefore("window.").substringBeforeLast(";")

    private val guardLock = Any()

    @Volatile
    private var guardPassedAt = Instant.DISTANT_PAST

    /**
     * New visitors are sent to a check page (/_c) that runs a proof of work and fingerprints the browser before
     * setting the site's trust cookies, which the app shares with the WebView: a WebView passes it here.
     */
    private fun passGuard(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestedAt = Clock.System.now()
        val response = chain.proceed(request)
        val checkUrl = response.request.url
        if (checkUrl.encodedPath != "/_c") return response
        response.close()
        synchronized(guardLock) {
            // Several requests can hit the check at once: pass it once, for all of them
            if (guardPassedAt < requestedAt) {
                val appUserAgent = request.header("User-Agent")!!
                runWebViewBlocking<Unit>(chain.call()) {
                    userAgent = appUserAgent
                    // The check page sends the WebView on to the requested page once passed
                    onPageStarted { url -> if (url.toHttpUrlOrNull()?.encodedPath != "/_c") resolve(Unit) }
                    loadUrl(checkUrl.toString())
                }
                guardPassedAt = Clock.System.now()
            }
        }
        return chain.proceed(request)
    }

    private companion object {
        val MANGA_PATH = Regex("""/\d+-[^/]+\.html""")
    }
}
