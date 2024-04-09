package eu.kanade.tachiyomi.extension.pt.mangaterra

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
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
import org.jsoup.parser.Parser
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaTerra : ParsedHttpSource() {
    override val lang: String = "pt-BR"
    override val supportsLatest: Boolean = true
    override val name: String = "Manga Terra"
    override val baseUrl: String = "https://manga-terra.com"

    override val client = network.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    private val json: Json by injectLazy()

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("h5")!!.ownText()
        date_upload = element.selectFirst("h5 > div")!!.ownText().toDate()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun chapterListSelector() = ".card-list-chapter a"

    override fun imageUrlParse(document: Document) = document.selectFirst("img")!!.absUrl("src")

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga?q=u&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".card-body h1")!!.ownText()
        description = document.selectFirst(".card-body p")?.ownText()
        thumbnail_url = document.selectFirst(".card-body img")?.absUrl("src")
        genre = document.select(".card-series-about a").joinToString { it.ownText() }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var page = 1
        do {
            val dom = client.newCall(GET("${document.location()}/${page++}", headers)).execute().asJsoup()
            pages += Page(index = page, imageUrl = imageUrlParse(dom))
        } while (dom.selectFirst(".btn-next") != null)

        return pages
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("p")!!.ownText()
        thumbnail_url = element.selectFirst("img")?.srcAttr()
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = ".pagination > .page-item:not(.disabled):last-child"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga?q=p&page=$page", headers)

    override fun popularMangaSelector(): String = ".card-body .row > div"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    private fun searchByQueryMangaParse(response: Response): MangasPage {
        val fragment = Jsoup.parse(
            json.decodeFromString<String>(response.body.string()),
            baseUrl,
            Parser.htmlParser(),
        )

        return MangasPage(
            mangas = fragment.select("div.grid-item-series").map(::searchMangaFromElement),
            hasNextPage = false,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("search")) {
            return searchByQueryMangaParse(response)
        }
        return super.searchMangaParse(response)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(slugPrefix)) {
            val slug = query.substringAfter(slugPrefix)
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

    override fun searchMangaSelector() = popularMangaSelector()

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch { fetchGenres() }
        val filters = mutableListOf(
            GenreFilter(
                title = "Categorias",
                genres = genresList,
            ),
        )
        return FilterList(filters)
    }

    private fun Element.srcAttr(): String = when {
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    private fun String.toDate() = try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    private var fetchGenresAttempts: Int = 0

    private fun fetchGenres() {
        if (fetchGenresAttempts < 3 && genresList.isEmpty()) {
            try {
                genresList = client.newCall(GET("$baseUrl/manga")).execute()
                    .use { parseGenres(it.asJsoup()) }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    private fun parseGenres(document: Document): List<Genre> {
        return document.select(".form-filters .custom-checkbox")
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
        val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale("pt", "BR"))
        val slugPrefix = "slug:"
    }
}

class GenreFilter(title: String, genres: List<Genre>) :
    Filter.Group<Genre>(title, genres)

class Genre(name: String, val query: String, val value: String) : Filter.CheckBox(name)
var genresList: List<Genre> = emptyList()
