package eu.kanade.tachiyomi.extension.en.multporn

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import rx.schedulers.Schedulers

class Multporn : HttpSource() {

    override val name = "Multporn"
    override val lang: String = "en"
    override val baseUrl = "https://multporn.net"
    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", HEADER_USER_AGENT)
        .add("Content-Type", HEADER_CONTENT_TYPE)

    // Popular

    private fun buildPopularMangaRequest(page: Int, filters: FilterList = FilterList()): Request {
        val url = "$baseUrl/best".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        (if (filters.isEmpty()) getMultpornFilterList(POPULAR_DEFAULT_SORT_BY_FILTER_STATE) else filters).forEach {
            when (it) {
                is SortBySelectFilter -> url.addQueryParameter("sort_by", it.selected.uri)
                is SortOrderSelectFilter -> url.addQueryParameter("sort_order", it.selected.uri)
                is PopularTypeSelectFilter -> url.addQueryParameter("type", it.selected.uri)
                else -> { }
            }
        }

        return GET(url.build(), headers)
    }

    override fun popularMangaRequest(page: Int) = buildPopularMangaRequest(page - 1)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector).firstOrNull() != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest

    private fun buildLatestMangaRequest(page: Int, filters: FilterList = FilterList()): Request {
        val url = "$baseUrl/new".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        (if (filters.isEmpty()) getMultpornFilterList(LATEST_DEFAULT_SORT_BY_FILTER_STATE) else filters).forEach {
            when (it) {
                is SortBySelectFilter -> url.addQueryParameter("sort_by", it.selected.uri)
                is SortOrderSelectFilter -> url.addQueryParameter("sort_order", it.selected.uri)
                is LatestTypeSelectFilter -> url.addQueryParameter("type", it.selected.uri)
                else -> { }
            }
        }

        return GET(url.build(), headers)
    }

    override fun latestUpdatesRequest(page: Int) = buildLatestMangaRequest(page - 1)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector).firstOrNull() != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search

    private fun textSearchFilterParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#content .col-1:contains(Views:),.col-2:contains(Views:)")
            .map { popularMangaFromElement(it) }

        val hasNextPage = document.select(popularMangaNextPageSelector).firstOrNull() != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun buildSearchMangaRequest(page: Int, query: String, filtersArg: FilterList = FilterList()): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("search_api_views_fulltext", query)

        (if (filtersArg.isEmpty()) getMultpornFilterList(SEARCH_DEFAULT_SORT_BY_FILTER_STATE) else filtersArg).forEach {
            when (it) {
                is SortBySelectFilter -> url.addQueryParameter("sort_by", it.selected.uri)
                is SearchTypeSelectFilter -> url.addQueryParameter("type", it.selected.uri)
                else -> { }
            }
        }

        return GET(url.build(), headers)
    }

    private fun buildTextSearchFilterRequests(page: Int, filters: List<TextSearchFilter>): List<Request> = filters.flatMap {
        it.stateURIs.map { queryURI ->
            GET("$baseUrl/${it.uri}/$queryURI?page=0,$page")
        }
    }

    private fun squashMangasPageObservables(observables: List<Observable<MangasPage?>>): Observable<MangasPage> = Observable.from(observables)
        .flatMap { it.observeOn(Schedulers.io()) }
        .toList()
        .map { it.filterNotNull() }
        .map { pages ->
            MangasPage(
                pages.flatMap { it.mangas }.distinctBy { it.url },
                pages.any { it.hasNextPage },
            )
        }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val sortByFilterType = filters.firstInstanceOrNull<SortBySelectFilter>()?.requestType ?: POPULAR_REQUEST_TYPE
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
        val sortByFilterType = filters.firstInstanceOrNull<SortBySelectFilter>()?.requestType ?: POPULAR_REQUEST_TYPE

        return when {
            query.isNotEmpty() || sortByFilterType == SEARCH_REQUEST_TYPE -> buildSearchMangaRequest(page - 1, query, filters)
            sortByFilterType == LATEST_REQUEST_TYPE -> buildLatestMangaRequest(page - 1, filters)
            else -> buildPopularMangaRequest(page - 1, filters)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector).firstOrNull() != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details

    private fun parseUnlabelledAuthorNames(document: Document): List<String> = listOf(
        "field-name-field-author",
        "field-name-field-authors-gr",
        "field-name-field-img-group",
        "field-name-field-hentai-img-group",
        "field-name-field-rule-63-section",
    ).flatMap { document.select(".$it a").map { a -> a.text() } }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select("h1#page-title").text()

        val infoMap = listOf(
            "Section",
            "Characters",
            "Tags",
            "Author",
        ).associateWith {
            document.select(".field:has(.field-label:contains($it:)) .links a").map { t -> t.text() }
        }

        artist = (infoMap.getValue("Author") + parseUnlabelledAuthorNames(document))
            .distinct().joinToString()
        author = artist

        genre = listOf("Tags", "Section", "Characters")
            .flatMap { infoMap.getValue(it) }.distinct().joinToString()

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

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

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

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".jb-image img").mapIndexed { i, image ->
            Page(i, imageUrl = image.absUrl("src").replace("/styles/juicebox_2k/public", "").substringBefore("?"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Selectors

    private val popularMangaSelector = ".masonry-item"
    private val popularMangaNextPageSelector = ".pager-next a"

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select(".views-field-title a").attr("abs:href"))
        title = element.select(".views-field-title").text()
        thumbnail_url = element.select("img").attr("abs:src")
    }

    // Filters

    override fun getFilterList() = getMultpornFilterList(POPULAR_DEFAULT_SORT_BY_FILTER_STATE)

    companion object {
        private const val HEADER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"
        private const val HEADER_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8"
    }
}
