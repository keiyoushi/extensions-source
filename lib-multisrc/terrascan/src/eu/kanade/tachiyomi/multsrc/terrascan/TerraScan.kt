package eu.kanade.tachiyomi.multisrc.terrascan

import eu.kanade.tachiyomi.network.GET
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

abstract class TerraScan(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale("pt", "BR")),
) : ParsedHttpSource() {

    override val supportsLatest: Boolean = true

    override val client = network.cloudflareClient

    private val noRedirectClient = network.cloudflareClient.newBuilder()
        .followRedirects(false)
        .build()

    private val json: Json by injectLazy()

    private var genresList: List<Genre> = emptyList()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga?q=p&page=$page", headers)

    open val popularMangaTitleSelector: String = "p, h3"
    open val popularMangaThumbnailSelector: String = "img"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(popularMangaTitleSelector)!!.ownText()
        thumbnail_url = element.selectFirst(popularMangaThumbnailSelector)?.srcAttr()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = ".pagination > .page-item:not(.disabled):last-child"

    override fun popularMangaSelector(): String = ".series-paginated .grid-item-series, .series-paginated .series"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (genresList.isEmpty()) {
            genresList = parseGenres(document)
        }
        val mangas = document.select(popularMangaSelector())
            .map(::popularMangaFromElement)

        return MangasPage(mangas, document.selectFirst(popularMangaNextPageSelector()) != null)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga?q=u&page=$page", headers)

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val slug = query.substringAfter(URL_SEARCH_PREFIX)
            return client.newCall(GET("$baseUrl/manga/$slug", headers))
                .asObservableSuccess().map { response ->
                    MangasPage(listOf(mangaDetailsParse(response)), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addPathSegment("search")
                .addQueryParameter("q", query)
            return GET(url.build(), headers)
        }

        url.addPathSegment("manga")

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state.forEach {
                        if (it.state) {
                            url.addQueryParameter(it.query, it.value)
                        }
                    }
                }
                else -> {}
            }
        }

        url.addQueryParameter("page", "$page")

        return GET(url.build(), headers)
    }

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = null

    override fun searchMangaSelector() = ".col-6.col-sm-3.col-md-3.col-lg-2.p-1"

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("search")) {
            return searchByQueryMangaParse(response)
        }
        return super.searchMangaParse(response)
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<out Any>>()
        if (genresList.isNotEmpty()) {
            filters += GenreFilter(
                title = "Gêneros",
                genres = genresList,
            )
        } else {
            filters += Filter.Header("Aperte 'Redefinir' mostrar os gêneros disponíveis")
        }
        return FilterList(filters)
    }

    open val mangaDetailsContainerSelector: String = "main"
    open val mangaDetailsTitleSelector: String = "h1"
    open val mangaDetailsThumbnailSelector: String = "img"
    open val mangaDetailsDescriptionSelector: String = "p"
    open val mangaDetailsGenreSelector: String = ".card:has(h5:contains(Categorias)) a, .card:has(h5:contains(Categorias)) div"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        with(document.selectFirst(mangaDetailsContainerSelector)!!) {
            title = selectFirst(mangaDetailsTitleSelector)!!.text()
            thumbnail_url = selectFirst(mangaDetailsThumbnailSelector)?.absUrl("href")
            description = selectFirst(mangaDetailsDescriptionSelector)?.text()
            genre = document.select(mangaDetailsGenreSelector)
                .joinToString { it.ownText() }
        }
        setUrlWithoutDomain(document.location())
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        with(element.selectFirst("h5")!!) {
            name = ownText()
            date_upload = selectFirst("div")!!.ownText().toDate()
        }
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun chapterListSelector() = ".col-chapter a"

    override fun pageListParse(document: Document): List<Page> {
        val mangaChapterUrl = document.location()
        val maxPage = findPageCount(mangaChapterUrl)
        return (1..maxPage).map { page -> Page(page - 1, "$mangaChapterUrl/$page") }
    }

    override fun imageUrlParse(document: Document) = document.selectFirst("main img")!!.srcAttr()

    private fun searchByQueryMangaParse(response: Response): MangasPage {
        val fragment = Jsoup.parseBodyFragment(
            json.decodeFromString<String>(response.body.string()),
            baseUrl,
        )

        return MangasPage(
            mangas = fragment.select(searchMangaSelector()).map(::searchMangaFromElement),
            hasNextPage = false,
        )
    }

    private fun findPageCount(pageUrl: String): Int {
        var lowerBound = 1
        var upperBound = 100

        while (lowerBound <= upperBound) {
            val midpoint = lowerBound + (upperBound - lowerBound) / 2

            val request = Request.Builder().apply {
                url("$pageUrl/$midpoint")
                headers(headers)
                head()
            }.build()

            val response = try {
                noRedirectClient.newCall(request).execute()
            } catch (e: Exception) {
                throw Exception("Failed to fetch $pageUrl")
            }

            if (response.code == 302) {
                upperBound = midpoint - 1
            } else {
                lowerBound = midpoint + 1
            }
        }

        return lowerBound
    }

    private fun Element.srcAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    private fun String.toDate() = try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    open val genreFilterSelector: String = "form div > div:has(input) div"

    private fun parseGenres(document: Document): List<Genre> {
        return document.select(genreFilterSelector)
            .map { element ->
                val input = element.selectFirst("input")!!
                Genre(
                    name = element.selectFirst("label")!!.ownText(),
                    query = input.attr("name"),
                    value = input.attr("value"),
                )
            }
    }

    companion object {
        const val URL_SEARCH_PREFIX = "slug:"
    }
}
