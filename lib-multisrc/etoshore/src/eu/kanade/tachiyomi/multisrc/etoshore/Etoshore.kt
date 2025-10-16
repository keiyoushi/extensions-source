package eu.kanade.tachiyomi.multisrc.etoshore

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

abstract class Etoshore(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    open val popularFilter = FilterList(
        SelectionList("", listOf(Tag(value = "views", query = "sort"))),
    )

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilter)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun popularMangaSelector() = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException()

    // ============================== Latest ===============================

    open val latestFilter = FilterList(
        SelectionList("", listOf(Tag(value = "date", query = "sort"))),
    )

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestFilter)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/page/$page".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
        filters.forEach { filter ->
            when (filter) {
                is SelectionList -> {
                    val selected = filter.selected().takeIf { it.value.isNotBlank() }
                        ?: return@forEach
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
            return fetchMangaDetails(SManga.create().apply { url = "/manga/$slug/" })
                .map { manga -> MangasPage(listOf(manga), false) }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = ".search-posts .chapter-box .poster a"

    override fun searchMangaNextPageSelector() = ".navigation .naviright:has(a)"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.attr("title")
        thumbnail_url = element.selectFirst("img")?.let(::imageFromElement)
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (filterList.isEmpty()) {
            filterParse(response)
        }
        return super.searchMangaParse(response)
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()

        description = document.selectFirst(".excerpt p")?.text()

        document.selectFirst(".details-right-con img")?.let { thumbnail_url = imageFromElement(it) }

        genre = document.select("div.meta-item span.meta-title:contains(Genres) + span a")
            .joinToString { it.text() }

        author = document.selectFirst("div.meta-item span.meta-title:contains(Author) + span a")
            ?.text()

        with(document) {
            status = when {
                containsClass(".finished") -> SManga.COMPLETED
                containsClass(".publishing") -> SManga.ONGOING
                containsClass(".on-hiatus") -> SManga.ON_HIATUS
                containsClass(".discontinued") -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }

        setUrlWithoutDomain(document.location())
    }

    private fun Element.containsClass(cssSelector: String) = select(cssSelector).isNotEmpty()

    protected open fun imageFromElement(element: Element): String? {
        val attributes = listOf(
            "data-src",
            "data-lazy-src",
            "data-cfsrc",
            "src",
        )
        return attributes
            .mapNotNull { attr -> element.takeIf { it.hasAttr(attr) }?.attr("abs:$attr") }
            .maxOrNull()
            ?: element.takeIf { it.hasAttr("srcset") }?.attr("abs:srcset")?.getSrcSetImage()
    }

    protected open fun String.getSrcSetImage(): String? {
        return this.split(" ")
            .filter(URL_REGEX::matches)
            .maxOfOrNull(String::toString)
    }

    // ============================== Chapters ============================

    override fun chapterListSelector() = ".chapter-list li a"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response)
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".title")!!.text()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // ============================== Pages ===============================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".chapter-images .chapter-item > img").mapIndexed { index, element ->
            Page(index, document.location(), imageFromElement(element))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================= Filters ==============================

    private var filterList = emptyList<Pair<String, List<Tag>>>()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        filters += if (filterList.isNotEmpty()) {
            filterList.map { SelectionList(it.first, it.second) }
        } else {
            listOf(Filter.Header("Aperte 'Redefinir' para tentar mostrar os filtros"))
        }

        return FilterList(filters)
    }

    protected open fun parseSelection(document: Document, selector: String): Pair<String, List<Tag>>? {
        val selectorFilter = "#filter-form $selector .select-item-head .text"

        return document.selectFirst(selectorFilter)?.text()?.let { displayName ->
            val tags = document.select("#filter-form $selector li").map { element ->
                element.selectFirst("input")!!.let { input ->
                    Tag(
                        name = element.selectFirst(".text")!!.text(),
                        value = input.attr("value"),
                        query = input.attr("name"),
                    )
                }
            }
            displayName to mutableListOf<Tag>().apply {
                this += Tag("Default")
                this += tags
            }
        }
    }

    open val filterListSelector: List<String> = listOf(
        ".filter-genre",
        ".filter-status",
        ".filter-type",
        ".filter-year",
        ".filter-sort",
    )

    open fun filterParse(response: Response) {
        val document = Jsoup.parseBodyFragment(response.peekBody(Long.MAX_VALUE).string())
        filterList = filterListSelector.mapNotNull { selector -> parseSelection(document, selector) }
    }

    protected data class Tag(val name: String = "", val value: String = "", val query: String = "")

    private open class SelectionList(displayName: String, private val vals: List<Tag>, state: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.name }.toTypedArray(), state) {
        fun selected() = vals[state]
    }

    // ============================= Utils ==============================

    private fun String.containsIn(array: Array<String>): Boolean {
        return this.lowercase() in array.map { it.lowercase() }
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
        val URL_REGEX = """^(https?://[^\s/$.?#].[^\s]*)$""".toRegex()
    }
}
