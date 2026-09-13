package eu.kanade.tachiyomi.extension.en.xoxocomics

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class XoxoComics : WPComics() {

    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)

    override val gmtOffset = null

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addNetworkInterceptor { chain ->
            val request = chain.request()
            if (!request.url.toString().endsWith("#imagereq")) {
                return@addNetworkInterceptor chain.proceed(request)
            }

            val response = chain.proceed(request)
            if (response.code == 404) { // 404 is returned even when the image is found
                response.newBuilder()
                    .code(200)
                    .build()
            } else {
                response
            }
        }
    }

    override val searchPath = "search-comic"
    override val popularPath = "hot-comic"

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/comic-update?page=$page"
        return parseMangaPage(client.get(url), latestUpdatesSelector(), ::latestUpdatesFromElement)
    }

    override fun latestUpdatesSelector() = "li.row"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        element.select("h3 a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = element.select("img").attr("data-original")
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotEmpty() || filters.isEmpty()) {
            // Search won't work together with filter
            baseUrl.toHttpUrl().newBuilder()
                .addPathSegment(searchPath)
                .addQueryParameter("keyword", query)
                .addQueryParameter("page", page.toString())
                .build()
        } else {
            val builder = baseUrl.toHttpUrl().newBuilder()

            var genreFilter: UriPartFilter? = null
            var statusFilter: UriPartFilter? = null
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> genreFilter = filter
                    is StatusFilter -> statusFilter = filter
                    else -> {}
                }
            }

            // Genre filter must come before status filter
            genreFilter?.toUriPart()?.let { builder.addPathSegment(it) }
            statusFilter?.toUriPart()?.let { builder.addQueryParameter("status", it) }

            builder.apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("sort", "0")
            }.build()
        }

        return parseMangaPage(client.get(url), searchMangaSelector(), ::searchMangaFromElement)
    }

    override suspend fun mangaUpdateParse(response: Response, manga: SManga, chapters: List<SChapter>): SMangaUpdate {
        val document = response.asJsoup()
        val updatedManga = mangaDetailsParse(document)

        val updatedChapters = run {
            val chapterList = mutableListOf<SChapter>()
            var doc = document
            while (true) {
                doc.select(chapterListSelector()).forEach { chapterList.add(chapterFromElement(it)) }
                val nextUrl = doc.select("ul.pagination a[rel=next]").firstOrNull()?.absUrl("href")
                    ?: break
                doc = client.get(nextUrl).asJsoup()
            }
            chapterList
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        date_upload = element.select("div.col-xs-3").text().toDate()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = parsePageList(client.get(baseUrl + "${chapter.url}/all"))

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/comic-list").asJsoup().let { document ->
        parseGenres(document).toJsonElement()
    }

    override val genresSelector = ".genres h2:contains(Genres) + ul.nav li a"

    override fun getFilterList(genres: List<Pair<String?, String>>): FilterList = FilterList(
        buildList {
            add(Filter.Header("Search query won't use Genre/Status filter"))
            addAll(super.getFilterList(genres))
        },
    )

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .url(page.imageUrl!! + "#imagereq")
        .build()
}
