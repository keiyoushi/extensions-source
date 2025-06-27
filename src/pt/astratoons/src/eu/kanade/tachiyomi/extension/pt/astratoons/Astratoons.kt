package eu.kanade.tachiyomi.extension.pt.astratoons

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Astratoons : ParsedHttpSource() {

    override val name = "Astratoons"

    override val baseUrl = "https://astratoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    // ======================== Popular ==========================

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = ".manga-list-item-detailed a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = null

    // ======================== Latest ==========================

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesSelector() = ".manga-card-simple"

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element).apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ======================== Search ==========================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters).map {
            it.copy(mangas = it.mangas.filter { it.title.contains(query, ignoreCase = true) })
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$baseUrl/manga/comics", headers)

    override fun searchMangaSelector() = ".comic-card a"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ======================== Details =========================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".sidebar-cover-image img")?.absUrl("src")
        description = document.selectFirst(".manga-description p")?.text()
        genre = document.select(".manga-tags a").joinToString { it.text() }
        author = document.selectFirst("dt:contains(Autor) + dd")?.text()
        artist = document.selectFirst("dt:contains(Artista) + dd")?.text()
        document.selectFirst(".status-tag")?.text()?.let {
            when (it.lowercase()) {
                "em andamento" -> SManga.ONGOING
                "hiato" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
    }

    // ======================== Chapter =========================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var page = 1
        val chapters = mutableListOf<SChapter>()
        do {
            val url = chapterListRequest(manga).url.newBuilder()
                .addQueryParameter("page", (page++).toString())
                .build()
            val document = client.newCall(GET(url, headers)).execute().asJsoup()
            chapters += document.select(chapterListSelector()).map(::chapterFromElement)
        } while (document.selectFirst("a[aria-label='Última']") != null)
        return Observable.just(chapters)
    }

    override fun chapterListSelector() = ".chapter-item-list .chapter-item a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".chapter-number")!!.text()
        date_upload = dateFormat.tryParse(element.selectFirst(".chapter-date")?.text())
        setUrlWithoutDomain(element.absUrl("href"))
    }

    // ======================== Pages ===========================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".chapter-image-canvas").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src-url"))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    companion object {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    }
}
