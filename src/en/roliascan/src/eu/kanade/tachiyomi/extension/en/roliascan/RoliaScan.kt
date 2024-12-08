package eu.kanade.tachiyomi.extension.en.roliascan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy

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
    private val popularFilter by lazy {
        FilterList(SelectionList("", listOf(ratingList.maxBy { it.value })))
    }

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilter)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaParse(response: Response): MangasPage {
        if (genreList.isEmpty()) {
            getFilters(response)
        }
        return super.popularMangaParse(response)
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
                is GenreList -> {
                    val genres = filter.state
                        .filter { it.state }
                        .joinToString(",") { it.id }

                    if (genres.isBlank()) {
                        return@forEach
                    }

                    url.addQueryParameter("_genres", genres)
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

        document.selectFirst("tr:has(th:contains(Status)) > td")?.text()?.let {
            status = when {
                it.contains("publishing", true) -> SManga.ONGOING
                it.contains("completed", true) -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
    }

    // ======================== Chapters =====================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()
        val url = "$baseUrl/wp-admin/admin-ajax.php"

        val document = client.newCall(chapterListRequest(manga)).execute()
            .asJsoup()

        val postId = document
            .select("input[name=current_page_id]")
            .attr("value")

        chapters += document.select(chapterListSelector()).map(::chapterFromElement)
        val step = 20
        var offset = step

        do {
            val formBuilder = FormBody.Builder()
                .add("action", "load_more_chapters")
                .add("post_id", postId)
                .add("offset", offset.toString())

            val chapterPage = client.newCall(POST(url, headers, formBuilder.build())).execute().asJsoup()
                .select(chapterListSelector())
                .map(::chapterFromElement)

            chapters += chapterPage
            offset += step
        } while (chapterPage.isNotEmpty())

        return Observable.just(chapters)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = super.chapterListRequest(manga).url.newBuilder()
            .addPathSegment("chapterlist")
            .build()
        return GET(url, headers)
    }

    override fun chapterListSelector() = "a.seenchapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.text()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // ======================== Pages ========================================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".manga-child-the-content img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ======================== Filters ======================================

    private var genreList = emptyList<Genre>()

    private val ratingList = listOf(
        Option("Any"),
        Option("★★★★★ (5)", "5"),
        Option("★★★★☆ (4)", "4"),
        Option("★★★☆☆ (3)", "3"),
        Option("★★☆☆☆ (2)", "2"),
        Option("★☆☆☆☆ (1)", "1"),
    ).map { it.copy(query = "_rating") }

    private var optionList = emptyList<Pair<String, List<Option>>>()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SelectionList("Rating", ratingList),
        )

        filters += optionList.flatMap {
            listOf(
                Filter.Separator(),
                SelectionList(it.first, it.second),
            )
        }

        filters += if (genreList.isNotEmpty()) {
            listOf(
                Filter.Separator(),
                GenreList(title = "Genres", genres = genreList),
            )
        } else {
            listOf(
                Filter.Separator(),
                Filter.Header("Press 'Reset' to attempt to show the genres, sort and years filters"),
            )
        }
        return FilterList(filters)
    }

    private fun getFilters(response: Response) {
        val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())

        val script = document.selectFirst("script:containsData(FWP_JSON)")?.data()
            ?: return

        script.getDocumentFragmentFilter(buildRegex("genres"))?.let {
            genreList = it.select(".facetwp-checkbox").map { element ->
                Genre(
                    name = element.selectFirst(".facetwp-display-value")!!.text(),
                    id = element.attr("data-value"),
                )
            }
        }

        val queries = listOf(
            "Sort" to "sort_posts",
            "Year" to "movies_series_year",
            "Author" to "movies_series_staff",
        )

        optionList = queries.map {
            it.first to getOptionList(buildRegex(it.second), script)
        }
    }

    private fun getOptionList(pattern: Regex, content: String, cssQuery: String = "option"): List<Option> {
        val query = pattern.find(content)?.groups?.get("query")?.value ?: return emptyList()
        return content.getDocumentFragmentFilter(pattern)
            ?.select(cssQuery)
            ?.map { element ->
                Option(
                    name = element.text(),
                    value = element.attr("value"),
                    query = "_$query",
                )
            } ?: emptyList()
    }

    private fun String.getDocumentFragmentFilter(pattern: Regex): Document? {
        return pattern.find(this)?.groups?.get("value")?.value?.let {
            val fragment = json.decodeFromString<String>(it)
            Jsoup.parseBodyFragment(fragment)
        }
    }

    private fun buildRegex(field: String) = """"(?<query>$field)":(?<value>"<[^,]+)""".toRegex()

    private data class Option(val name: String = "", val value: String = "", val query: String = "")

    private open class SelectionList(displayName: String, private val vals: List<Option>, state: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.name }.toTypedArray(), state) {
        fun selected() = vals[state]
    }

    private class GenreList(title: String, genres: List<Genre>) :
        Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.id) })

    class GenreCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

    class Genre(val name: String, val id: String = name.lowercase().replace(" ", "-"))

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
