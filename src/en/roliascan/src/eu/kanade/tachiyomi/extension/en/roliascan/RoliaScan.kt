package eu.kanade.tachiyomi.extension.en.roliascan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Locale.getDefault

// Theme: AnimaCEWP
class RoliaScan : ParsedHttpSource() {

    override val name = "Rolia Scan"

    override val baseUrl = "https://roliascan.com"

    override val lang = "en"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // ======================== Popular ======================================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/wp-content/themes/animacewp/most_viewed_series.json", headers)

    override fun popularMangaSelector() = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<PopularWrapper>()
            .mangas.map(MangaDto::toSManga)
            .filter { it.title.isNotEmpty() }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ======================== Latest =======================================

    private val latestFilter = FilterList(
        SelectionList("", listOf(Option("", "update_oldest", query = "_sort_posts"))),
    )

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestFilter)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    // ======================== Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addQueryParameter("_post_type_search_box", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SelectionList -> {
                    val selected = filter.selected()
                    if (selected.value.isBlank()) {
                        return@forEach
                    }
                    url.addQueryParameter(selected.query, selected.value)
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.substringAfter(PREFIX_SEARCH)
            return fetchMangaDetails(SManga.create().apply { url = "/manga/$slug" })
                .map { manga -> MangasPage(listOf(manga), false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = "div.post"

    override fun searchMangaNextPageSelector() = null

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        element.selectFirst("h6 a")!!.let {
            title = it.text()
            setUrlWithoutDomain(it.absUrl("href"))
        }
    }

    // ======================== Details ======================================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document
            .selectFirst("div.post-type-single-column img.wp-post-image")
            ?.absUrl("src")
        description = document
            .select("div.card-body:has(h5:contains(Synopsis)) p")
            .filter { p -> p.text().isNotBlank() }
            .joinToString("\n") { it.text() }

        genre = document.select("a[href*=genres]")
            .joinToString { it.text() }

        artist = document.selectFirst("tr:has(th:contains(Artist)) > td")?.text()

        document.selectFirst("tr:has(th:contains(Status)) > td")?.text()?.let {
            status = when {
                it.contains("publishing", true) -> SManga.ONGOING
                it.contains("ongoing", true) -> SManga.ONGOING
                it.contains("hiatus", true) -> SManga.ON_HIATUS
                it.contains("completed", true) -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
    }

    // ======================== Chapters =====================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()

        var page = 1

        do {
            val document = client.newCall(chapterListRequest(page++, manga)).execute().asJsoup()
            chapters += document
                .select(chapterListSelector())
                .map(::chapterFromElement)
        } while (document.selectFirst(chapterListNextPageSelector) != null)

        return Observable.just(chapters)
    }

    private fun chapterListRequest(page: Int, manga: SManga): Request {
        val url = super.chapterListRequest(manga).url.newBuilder()
            .addEncodedPathSegments("chapterlist/")
            .addQueryParameter("chap_page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()

    override fun chapterListSelector() = ".chapter-list-row:has(.chapter-cell)"

    private val chapterListNextPageSelector = "a[class=page-link]:contains(Next)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        with(element.selectFirst("a.seenchapter")!!) {
            name = text()
            setUrlWithoutDomain(absUrl("href"))
        }
        element.selectFirst(".chapter-date")?.text()?.let {
            date_upload = DATE_FORMAT.tryParse(it)
        }
    }

    // ======================== Pages ========================================

    override fun pageListParse(document: Document): List<Page> = document.select(".manga-child-the-content img").mapIndexed { index, element ->
        Page(index, imageUrl = element.absUrl("src"))
    }

    override fun imageUrlParse(document: Document) = ""

    // ======================== Filters ======================================

    private var optionList = emptyList<Pair<String, List<Option>>>()

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun getFilterList(): FilterList {
        if (optionList.isEmpty()) {
            scope.launch { getFilters() }
        }

        val filters = mutableListOf<Filter<*>>()

        filters += if (optionList.isNotEmpty()) {
            optionList.flatMap {
                listOf(
                    SelectionList(it.first, it.second),
                )
            }
        } else {
            listOf(
                Filter.Separator(),
                Filter.Header("Press 'Reset' to attempt to show the genres, sort and years filters"),
            )
        }
        return FilterList(filters)
    }

    private fun getFilters() {
        try {
            parseFilters(client.newCall(searchMangaRequest(0, "", FilterList())).execute())
        } catch (_: Exception) { }
    }

    private fun parseFilters(response: Response) {
        val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())

        val script = document.selectFirst("script:containsData(FWP_JSON)")?.data()
            ?: return

        val queries = listOf(
            "Type" to "mtype",
            "Genres" to "genres",
            "Status" to "status",
            "Sort" to "sort_posts",
            "Year" to "movies_series_year",
            "Publisher" to "movies_series_year",
        )

        optionList = queries.map {
            it.first to getOptionList(buildRegex(it.second), script)
        }.filter { it.second.isNotEmpty() }
    }

    private fun getOptionList(pattern: Regex, content: String, cssQuery: String = "option"): List<Option> {
        val query = pattern.find(content)?.groups?.get(1)?.value ?: return emptyList()
        return content.getDocumentFragmentFilter(pattern)
            ?.select(cssQuery)
            ?.map { element ->
                Option(
                    name = element.text().replace(SUFFIX_REGEX, "")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() },
                    value = element.attr("value"),
                    query = "_$query",
                )
            } ?: emptyList()
    }

    private fun String.getDocumentFragmentFilter(pattern: Regex): Document? = pattern.find(this)?.groups?.get(2)?.value?.let {
        val fragment = json.decodeFromString<String>(it)
        Jsoup.parseBodyFragment(fragment)
    }

    private fun buildRegex(field: String) = """"($field)":("<[^,]+)""".toRegex()

    private data class Option(val name: String = "", val value: String = "", val query: String = "")

    private open class SelectionList(displayName: String, private val vals: List<Option>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.name }.toTypedArray(), state) {
        fun selected() = vals[state]
    }

    companion object {
        private val SUFFIX_REGEX = """\(\d+\)""".toRegex()
        private val DATE_FORMAT = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
        const val PREFIX_SEARCH = "id:"
    }
}
