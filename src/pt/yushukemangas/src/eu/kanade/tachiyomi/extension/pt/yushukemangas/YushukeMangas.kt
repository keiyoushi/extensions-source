package eu.kanade.tachiyomi.extension.pt.yushukemangas

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class YushukeMangas : ParsedHttpSource() {

    override val name = "Yushuke Mangas"

    override val baseUrl = "https://yushukemangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = ".popular-manga-widget .popular-manga-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = null

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesSelector() = ".manga-list .manga-item"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun latestUpdatesNextPageSelector() = ".pagination-next"

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", "1")
            .build()
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val selected = filter.selected()
                    if (selected == all) return@forEach
                    url = "$baseUrl/generos.php".toHttpUrl().newBuilder()
                        .addQueryParameter("genre", selected)
                        .addQueryParameter("search", query)
                        .build()
                }
                else -> {}
            }
        }
        return GET(url, headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val id = query.substringAfter(PREFIX_SEARCH)
            return client.newCall(GET("$baseUrl/manga?id=$id", headers))
                .asObservableSuccess()
                .map {
                    val manga = mangaDetailsParse(it.asJsoup())
                    MangasPage(listOf(manga), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = "${latestUpdatesSelector()}, a.search-item"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3, .search-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain((element.selectFirst("a") ?: element).absUrl("href"))
    }

    override fun searchMangaNextPageSelector() = null

    // ============================== Manga Details =========================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val details = document.selectFirst(".manga-header")!!
        title = details.selectFirst("h1")!!.text()
        thumbnail_url = details.selectFirst(".manga-image img")?.absUrl("src")
        genre = details.select(".manga-generos .genre-button").joinToString { it.text() }
        description = details.selectFirst("p.manga-sinopse")?.text()
        setUrlWithoutDomain(document.location())
    }

    // ============================== Chapters ===============================

    override fun chapterListSelector() = ".chapter-list .chapter-item a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".chapter-number")!!.text()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    private fun chapterListNextPageSelector() = latestUpdatesNextPageSelector()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val document = fetchChapterListPage(manga, page++)
            chapters += document.select(chapterListSelector()).map(::chapterFromElement)
        } while (document.selectFirst(chapterListNextPageSelector()) != null)
        return Observable.just(chapters)
    }

    private fun fetchChapterListPage(manga: SManga, page: Int): Document {
        return client
            .newCall(GET("$baseUrl${manga.url}&page=$page", headers))
            .execute().asJsoup()
    }

    // ============================== Pages ===============================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".chapter-images img").mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl.absUrl("src"))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================== Filters ===============================

    open class GenreFilter(displayName: String, private val vals: Array<String>, state: Int = 0) :
        Filter.Select<String>(displayName, vals, state) {
        fun selected() = vals[state]
    }

    override fun getFilterList() = FilterList(GenreFilter("Gêneros", genresList))

    private val all = "Todos"

    private val genresList = arrayOf(
        all, "+18", "Abuso", "Adulto", "Amor Puro", "Artes Marciais",
        "Aventura", "Ação", "Comédia", "Crime", "Cultivação", "Drama",
        "Fantasia", "Gap Girls", "Gore", "Harém", "Histórico", "Horror",
        "Isekai", "Mistério", "Overpowered", "Psicológico", "Reencarnação",
        "Romance", "Sistema", "Tragédia", "Viagem no Tempo", "Violência",
    )

    companion object {
        const val PREFIX_SEARCH = "id:"
    }
}
