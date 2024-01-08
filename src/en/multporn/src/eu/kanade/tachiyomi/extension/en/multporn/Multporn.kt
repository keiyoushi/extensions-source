package eu.kanade.tachiyomi.extension.en.multporn

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import java.util.Locale

class Multporn : ParsedHttpSource() {

    override val name = "Multporn"
    override val lang: String = "en"
    override val baseUrl = "https://multporn.net"
    override val supportsLatest = true

    private val json: Json by injectLazy()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", HEADER_USER_AGENT)
        .add("Content-Type", HEADER_CONTENT_TYPE)

    // Popular

    private fun buildPopularMangaRequest(page: Int, filters: FilterList = FilterList()): Request {
        val body = FormBody.Builder()
            .addEncoded("page", page.toString())
            .addEncoded("view_name", "top")
            .addEncoded("view_display_id", "page")

        (if (filters.isEmpty()) getFilterList(POPULAR_DEFAULT_SORT_BY_FILTER_STATE) else filters).forEach {
            when (it) {
                is SortBySelectFilter -> body.addEncoded("sort_by", it.selected.uri)
                is SortOrderSelectFilter -> body.addEncoded("sort_order", it.selected.uri)
                is PopularTypeSelectFilter -> body.addEncoded("type", it.selected.uri)
                else -> { }
            }
        }

        return POST("$baseUrl/views/ajax", headers, body.build())
    }

    override fun popularMangaRequest(page: Int) = buildPopularMangaRequest(page - 1)

    override fun popularMangaParse(response: Response): MangasPage {
        val html = json.decodeFromString<JsonArray>(response.body.string())
            .last { it.jsonObject["command"]!!.jsonPrimitive.content == "insert" }.jsonObject["data"]!!.jsonPrimitive.content

        return super.popularMangaParse(
            response.newBuilder()
                .body(html.toResponseBody("text/html; charset=UTF-8".toMediaTypeOrNull()))
                .build(),
        )
    }

    override fun popularMangaSelector() = ".masonry-item"
    override fun popularMangaNextPageSelector() = ".pager-next a"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        url = element.select(".views-field-title a").attr("href")
        title = element.select(".views-field-title").text()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    // Latest

    private fun buildLatestMangaRequest(page: Int, filters: FilterList = FilterList()): Request {
        val url = "$baseUrl/new".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())

        (if (filters.isEmpty()) getFilterList(LATEST_DEFAULT_SORT_BY_FILTER_STATE) else filters).forEach {
            when (it) {
                is SortBySelectFilter -> url.addQueryParameter("sort_by", it.selected.uri)
                is SortOrderSelectFilter -> url.addQueryParameter("sort_order", it.selected.uri)
                is LatestTypeSelectFilter -> url.addQueryParameter("type", it.selected.uri)
                else -> { }
            }
        }

        return GET(url.toString(), headers)
    }

    override fun latestUpdatesRequest(page: Int) = buildLatestMangaRequest(page - 1)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    // Search

    private fun textSearchFilterParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#content .col-1:contains(Views:),.col-2:contains(Views:)")
            .map { popularMangaFromElement(it) }

        val hasNextPage = popularMangaNextPageSelector().let {
            document.select(it).firstOrNull()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun buildSearchMangaRequest(page: Int, query: String, filtersArg: FilterList = FilterList()): Request {
        val url = "$baseUrl/search".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("views_fulltext", query)

        (if (filtersArg.isEmpty()) getFilterList(SEARCH_DEFAULT_SORT_BY_FILTER_STATE) else filtersArg).forEach {
            when (it) {
                is SortBySelectFilter -> url.addQueryParameter("sort_by", it.selected.uri)
                is SearchTypeSelectFilter -> url.addQueryParameter("type", it.selected.uri)
                else -> { }
            }
        }

        return GET(url.toString(), headers)
    }

    private fun buildTextSearchFilterRequests(page: Int, filters: List<TextSearchFilter>): List<Request> {
        return filters.map {
            it.stateURIs.map { queryURI ->
                GET("$baseUrl/${it.uri}/$queryURI?page=0,$page")
            }
        }.flatten()
    }

    private fun squashMangasPageObservables(observables: List<Observable<MangasPage?>>): Observable<MangasPage> {
        return Observable.from(observables)
            .flatMap { it.observeOn(Schedulers.io()) }
            .toList()
            .map { it.filterNotNull() }
            .map { pages ->
                MangasPage(
                    pages.map { it.mangas }.flatten().distinctBy { it.url },
                    pages.any { it.hasNextPage },
                )
            }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val sortByFilterType = filters.findInstance<SortBySelectFilter>()?.requestType ?: POPULAR_REQUEST_TYPE
        val textSearchFilters = filters.filterIsInstance<TextSearchFilter>().filter { it.state.isNotBlank() }

        return when {
            textSearchFilters.isNotEmpty() -> {
                val requests = buildTextSearchFilterRequests(page - 1, textSearchFilters)

                squashMangasPageObservables(
                    requests.map {
                        client.newCall(it).asObservable().map { res ->
                            if (res.code == 200) {
                                textSearchFilterParse(res)
                            } else {
                                null
                            }
                        }
                    },
                )
            }
            query.isNotEmpty() || sortByFilterType == SEARCH_REQUEST_TYPE -> {
                val request = buildSearchMangaRequest(page - 1, query, filters)
                client.newCall(request).asObservableSuccess().map { searchMangaParse(it) }
            }
            sortByFilterType == LATEST_REQUEST_TYPE -> {
                val request = buildLatestMangaRequest(page - 1, filters)
                client.newCall(request).asObservableSuccess().map { latestUpdatesParse(it) }
            }
            else -> {
                val request = buildPopularMangaRequest(page - 1, filters)
                client.newCall(request).asObservableSuccess().map { popularMangaParse(it) }
            }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortByFilterType = filters.findInstance<SortBySelectFilter>()?.requestType ?: POPULAR_REQUEST_TYPE

        return when {
            query.isNotEmpty() || sortByFilterType == SEARCH_REQUEST_TYPE -> buildSearchMangaRequest(page - 1, query, filters)
            sortByFilterType == LATEST_REQUEST_TYPE -> buildLatestMangaRequest(page - 1, filters)
            else -> buildPopularMangaRequest(page - 1, filters)
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    // Details

    private fun parseUnlabelledAuthorNames(document: Document): List<String> = listOf(
        "field-name-field-author",
        "field-name-field-authors-gr",
        "field-name-field-img-group",
        "field-name-field-hentai-img-group",
        "field-name-field-rule-63-section",
    ).map { document.select(".$it a").map { a -> a.text().trim() } }.flatten()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1#page-title").text()

            val infoMap = listOf(
                "Section",
                "Characters",
                "Tags",
                "Author",
            ).map {
                it to document.select(".field:has(.field-label:contains($it:)) .links a").map { t -> t.text().trim() }
            }.toMap()

            artist = (infoMap.getValue("Author") + parseUnlabelledAuthorNames(document))
                .distinct().joinToString()
            author = artist

            genre = listOf("Tags", "Section", "Characters")
                .map { infoMap.getValue(it) }.flatten().distinct().joinToString()

            status = infoMap["Section"]?.firstOrNull { it == "Ongoings" }?.let { SManga.ONGOING } ?: SManga.COMPLETED

            val pageCount = document.select(".jb-image img").size

            description = infoMap
                .filter { it.key in arrayOf("Section", "Characters") }
                .filter { it.value.isNotEmpty() }
                .map { "${it.key}:\n${it.value.joinToString()}" }
                .let {
                    it + listOf(
                        "Pages:\n$pageCount",
                    )
                }
                .joinToString("\n\n")
        }
    }

    // Chapters

    override fun chapterListSelector(): String = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(
        listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                chapter_number = 1f
            },
        ),
    )

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".jb-image img").mapIndexed { i, image ->
            Page(i, imageUrl = image.attr("abs:src").replace("/styles/juicebox_2k/public", "").substringBefore("?"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = getFilterList(POPULAR_DEFAULT_SORT_BY_FILTER_STATE)

    open class URIFilter(open val name: String, open val uri: String)

    class RequestTypeURIFilter(
        val requestType: String,
        override var name: String,
        override val uri: String,
    ) : URIFilter(name, uri)

    open class URISelectFilter(name: String, open val filters: List<URIFilter>, state: Int = 0) :
        Filter.Select<String>(name, filters.map { it.name }.toTypedArray(), state) {
        open val selected: URIFilter
            get() = filters[state]
    }

    open class TypeSelectFilter(name: String, filters: List<URIFilter>) : URISelectFilter(name, filters)

    private class PopularTypeSelectFilter(filters: List<URIFilter>) : TypeSelectFilter("Popular Type", filters)

    private class LatestTypeSelectFilter(filters: List<URIFilter>) : TypeSelectFilter("Latest Type", filters)

    private class SearchTypeSelectFilter(filters: List<URIFilter>) : TypeSelectFilter("Search Type", filters)

    open class TextSearchFilter(name: String, val uri: String) : Filter.Text(name) {
        val stateURIs: List<String>
            get() {
                return state.split(",").filter { it != "" }.map {
                    Regex("[^A-Za-z0-9]").replace(it, " ").trim()
                        .replace("\\s+".toRegex(), "_").lowercase(Locale.getDefault())
                }.distinct()
            }
    }

    private class SortBySelectFilter(override val filters: List<RequestTypeURIFilter>, state: Int) :
        URISelectFilter(
            "Sort By",
            filters.map { filter ->
                filter.let { it.name = "[${it.requestType}] ${it.name}"; it }
            },
            state,
        ) {
        val requestType: String
            get() = filters[state].requestType
    }

    private class SortOrderSelectFilter(filters: List<URIFilter>) : URISelectFilter("Order By", filters)

    private fun getFilterList(sortByFilterState: Int) = FilterList(
        Filter.Header("Text search only works with Relevance and Author"),
        SortBySelectFilter(getSortByFilters(), sortByFilterState),
        Filter.Header("Order By only works with Popular and Latest"),
        SortOrderSelectFilter(getSortOrderFilters()),
        Filter.Header("Type filters apply based on selected Sort By option"),
        PopularTypeSelectFilter(getPopularTypeFilters()),
        LatestTypeSelectFilter(getLatestTypeFilters()),
        SearchTypeSelectFilter(getSearchTypeFilters()),
        Filter.Separator(),
        Filter.Header("Filters below ignore text search and all options above"),
        Filter.Header("Query must match title's non-special characters"),
        Filter.Header("Separate queries with comma (,)"),
        TextSearchFilter("Comic Tags", "category"),
        TextSearchFilter("Comic Characters", "characters"),
        TextSearchFilter("Comic Authors", "authors_comics"),
        TextSearchFilter("Comic Sections", "comics"),
        TextSearchFilter("Manga Categories", "category_hentai"),
        TextSearchFilter("Manga Characters", "characters_hentai"),
        TextSearchFilter("Manga Authors", "authors_hentai_comics"),
        TextSearchFilter("Manga Sections", "hentai_manga"),
        TextSearchFilter("Picture Authors", "authors_albums"),
        TextSearchFilter("Picture Sections", "pictures"),
        TextSearchFilter("Hentai Sections", "hentai"),
        TextSearchFilter("Rule 63 Sections", "rule_63"),
        TextSearchFilter("Gay Tags", "category_gay"),
    )

    private fun getPopularTypeFilters() = listOf(
        URIFilter("Comics", "1"),
        URIFilter("Hentai Manga", "2"),
        URIFilter("Cartoon Pictures", "3"),
        URIFilter("Hentai Pictures", "4"),
        URIFilter("Rule 63", "10"),
        URIFilter("Author Albums", "11"),
    )

    private fun getLatestTypeFilters() = listOf(
        URIFilter("Comics", "1"),
        URIFilter("Hentai Manga", "2"),
        URIFilter("Cartoon Pictures", "3"),
        URIFilter("Hentai Pictures", "4"),
        URIFilter("Author Albums", "10"),
    )

    private fun getSearchTypeFilters() = listOf(
        URIFilter("Comics", "1"),
        URIFilter("Hentai Manga", "2"),
        URIFilter("Gay Comics", "3"),
        URIFilter("Cartoon Pictures", "4"),
        URIFilter("Hentai Pictures", "5"),
        URIFilter("Rule 63", "11"),
        URIFilter("Humor", "13"),
    )

    private fun getSortByFilters() = listOf(
        RequestTypeURIFilter(POPULAR_REQUEST_TYPE, "Total Views", "totalcount_1"),
        RequestTypeURIFilter(POPULAR_REQUEST_TYPE, "Views Today", "daycount"),
        RequestTypeURIFilter(POPULAR_REQUEST_TYPE, "Last Viewed", "timestamp"),
        RequestTypeURIFilter(LATEST_REQUEST_TYPE, "Date Posted", "created"),
        RequestTypeURIFilter(LATEST_REQUEST_TYPE, "Date Updated", "changed"),
        RequestTypeURIFilter(SEARCH_REQUEST_TYPE, "Relevance", "search_api_relevance"),
        RequestTypeURIFilter(SEARCH_REQUEST_TYPE, "Author", "author"),
    )

    private fun getSortOrderFilters() = listOf(
        URIFilter("Descending", "DESC"),
        URIFilter("Ascending", "ASC"),
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    companion object {

        const val LATEST_DEFAULT_SORT_BY_FILTER_STATE = 3
        const val POPULAR_DEFAULT_SORT_BY_FILTER_STATE = 0
        const val SEARCH_DEFAULT_SORT_BY_FILTER_STATE = 5

        const val LATEST_REQUEST_TYPE = "Latest"
        const val POPULAR_REQUEST_TYPE = "Popular"
        const val SEARCH_REQUEST_TYPE = "Search"

        const val HEADER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"
        const val HEADER_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8"
    }
}
