package eu.kanade.tachiyomi.extension.en.babesource

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class BabeSource : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3) { it.host == baseUrl.toHttpUrl().host && !it.encodedPath.startsWith("/media/") }
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val url = chain.request().url
            if (!response.isSuccessful || url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "galleries") return@addNetworkInterceptor response
            // Gallery pages are sent no-cache; details, related and pages read the same page
            response.newBuilder()
                .header("Cache-Control", "max-age=600")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }

    private val gateClient by lazy { client.newBuilder().followRedirects(false).build() }

    override suspend fun getPopularManga(page: Int) = getListing("$baseUrl/most-viewed/".toHttpUrl(), page)

    override suspend fun getLatestUpdates(page: Int) = getListing("$baseUrl/newest/".toHttpUrl(), page)

    // Search path the site's search gate resolved for the last query
    private var searchPath: Pair<String, HttpUrl>? = null

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().selected
        val listPath = filters.firstInstanceOrNull<PaysiteFilter>()?.selected
            ?: filters.firstInstanceOrNull<CategoryFilter>()?.selected
            ?: filters.firstInstanceOrNull<TagFilter>()?.selected
        val listUrl = when {
            query.isNotBlank() -> resolveSearch(query.trim())?.sorted(sort) ?: return MangasPage(emptyList(), false)
            listPath != null -> "$baseUrl/$listPath/".toHttpUrl().sorted(sort)
            else -> "$baseUrl/${sort.globalPath}/".toHttpUrl()
        }
        return getListing(listUrl, page)
    }

    private suspend fun resolveSearch(query: String): HttpUrl? {
        searchPath?.takeIf { it.first == query }?.let { return it.second }

        val gateUrl = "$baseUrl/searchgate.php".toHttpUrl().newBuilder()
            .addQueryParameter("mode", "search")
            .addQueryParameter("type", "photos")
            .addQueryParameter("q", query)
            .build()
        val location = gateClient.get(gateUrl, Fetch.DOCUMENT, ensureSuccess = false).use { response ->
            response.header("Location") ?: throw HttpException(response.code)
        }
        // Queries without letters or digits redirect to the homepage
        val url = gateUrl.resolve(location)!!.takeIf { it.pathSegments[0] == "search" } ?: return null
        searchPath = query to url
        return url
    }

    private fun HttpUrl.sorted(sort: SortOption) = newBuilder().addPathSegment(sort.listPath).addPathSegment("").build()

    private suspend fun getListing(url: HttpUrl, page: Int): MangasPage {
        val pageUrl = if (page > 1) url.newBuilder().setPathSegment(url.pathSize - 1, "page$page.html").build() else url
        val document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select(".main-content__cards .main-content__tumba").mapNotNull(::galleryCard)
        return MangasPage(mangas, document.selectFirst("a.paginations__next[rel=next]") != null)
    }

    private fun galleryCard(card: Element): SManga? {
        val link = card.selectFirst("a.main-content__card-link[href*=/galleries/]") ?: return null
        val image = link.selectFirst("img")!!
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = image.attrOrNull("alt")!!
            thumbnail_url = image.absUrl("src")
        }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        return when (url.pathSegments[0]) {
            "pornstars", "paysites", "channels", "search" -> {
                val segments = url.pathSegments.filter { it.isNotEmpty() && !PAGE_SEGMENT.matches(it) }
                val listUrl = url.newBuilder().query(null).encodedPath("/").apply {
                    segments.forEach(::addPathSegment)
                    addPathSegment("")
                }.build()
                getListing(listUrl, page)
            }
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "galleries" || url.pathSegments.size != 2 || !url.pathSegments[1].endsWith(".html")) return null
        val manga = SManga.create().apply { this.url = url.encodedPath }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val aside = document.selectFirst("aside.aside-setting")!!
        val paysite = aside.selectFirst(".aside-setting__chapter--paysite a")
        val paysiteName = paysite?.selectFirst(".aside-link-text")?.text()
        val models = aside.select(".aside-setting__chapter--models a").mapNotNull { link ->
            link.selectFirst("strong")?.text()?.let { it to link.absUrl("href") }
        }
        val (dates, counts) = aside.select(".aside-setting__views-item span:last-child").map { it.text() }.partition(DATE_REGEX::matches)

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("title")!!.text().substringBeforeLast(" | ")
            author = models.joinToString { it.first }.ifEmpty { null }
            artist = paysiteName
            genre = (aside.select(".aside-setting__chapter--categories a") + aside.select("a.aside-setting__category-link"))
                .map { it.text() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString().ifEmpty { null }
            description = MarkdownDescription.Builder()
                .info(
                    aside.selectFirst("progress")?.attr("value")?.let { "$it% liked" },
                    aside.selectFirst(".bs-vote-count")?.text(),
                    counts.firstOrNull()?.let { "$it views" },
                    "${document.select(PAGE_SELECTOR).size} photos",
                )
                .links(listOfNotNull(paysiteName?.let { it to paysite.absUrl("href") }), "Paysite")
                .links(models, "Models")
                .build()
            thumbnail_url = manga.thumbnail_url ?: document.selectFirst("$PAGE_SELECTOR img")?.absUrl("src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Gallery"
            date_upload = DATE_FORMAT.tryParseDate(dates.firstOrNull())
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("#bs-rel-cards .main-content__tumba").mapNotNull(::galleryCard)

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(PAGE_SELECTOR).mapIndexed { index, link -> Page(index, imageUrl = link.absUrl("href")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val categories = async {
            client.get("$baseUrl/channels/", Fetch.DOCUMENT).asJsoup()
                .let { document ->
                    val channels = document.select(".main-content__cards .main-content__tumba").map { card ->
                        ListOption(card.selectFirst(".main-content__title")!!.text(), card.selectFirst("a.main-content__card-link")!!.listPath())
                    }
                    val tags = document.select("a.mobile-menu__dropdown-link[href*=/search/]").map { link ->
                        ListOption(link.text(), link.listPath())
                    }
                    channels to tags
                }
        }
        val paysites = async {
            client.get("$baseUrl/paysites/", Fetch.DOCUMENT).asJsoup()
                .select("a.pornstars-lists__link[href*=/paysites/]").map { link ->
                    ListOption(link.selectFirst(".pornstars-lists__labe")!!.text(), link.listPath())
                }
        }
        val (categoryList, tags) = categories.await()
        FilterData(categoryList, paysites.await(), tags).toJsonElement()
    }

    private fun Element.listPath(): String = absUrl("href").toHttpUrl().pathSegments.filter { it.isNotEmpty() }.joinToString("/")

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        return FilterList(
            listOfNotNull(
                SortFilter(),
                filterData?.let { Filter.Header("Text search, then Paysite, Category and Tag: only the first one set is used") },
                filterData?.let { PaysiteFilter(it.paysites) },
                filterData?.let { CategoryFilter(it.categories) },
                filterData?.let { TagFilter(it.tags) },
            ),
        )
    }

    companion object {
        private const val PAGE_SELECTOR = "a.slideshowGalleryImage"
        private val DATE_REGEX = Regex("""\d{4}-\d{2}-\d{2}""")
        private val PAGE_SEGMENT = Regex("""page\d+\.html""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    }
}
