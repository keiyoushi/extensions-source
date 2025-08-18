package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Niadd : ParsedHttpSource() {

    override val name = "Niadd"

    override val baseUrl = "https://www.niadd.com"

    override val lang = "en"

    override val supportsLatest = true

    // ============================= Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/category/Updated.html?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.manga_pic_list li"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.url = element.selectFirst("a")!!.attr("href")
        manga.title = element.selectFirst("a")!!.attr("title")
        manga.thumbnail_url = element.selectFirst("img")!!.attr("src")
        return manga
    }

    override fun popularMangaNextPageSelector() = "a.next"

    // ============================= Latest ==============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/category/Latest.html?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ============================= Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/?keyword=$query&page=$page", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ============================= Details ==============================

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text().orEmpty()
        manga.author = document.select("li:contains(Author:) a").joinToString { it.text() }
        manga.artist = document.select("li:contains(Artist:) a").joinToString { it.text() }
        manga.genre = document.select("li:contains(Genre:) a").joinToString { it.text() }
        manga.description = document.selectFirst("div.leftBox p")?.text()
        manga.thumbnail_url = document.selectFirst("div.mangaDetailTop img")?.attr("src")
        return manga
    }

    // ============================= Chapters ==============================

    override fun chapterListSelector() = "ul.chapter-list li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val a = element.selectFirst("a")!!
        chapter.url = a.attr("href") // pode vir com baseUrl antigo ou novo
        chapter.name = a.text()
        return chapter
    }

    // ============================= Pages ==============================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url

        // Se já for link absoluto, usa direto; senão concatena com baseUrl
        val finalUrl = if (chapterUrl.startsWith("http")) {
            "$chapterUrl?load=10&start=1"
        } else {
            "$baseUrl$chapterUrl?load=10&start=1"
        }

        return GET(finalUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        var pageNumber = 1

        // cada "pic_box" contém uma imagem do capítulo
        document.select("div.pic_box img").forEach { img ->
            val imageUrl = img.attr("src")
            pages.add(Page(pageNumber++, "", imageUrl))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }

    // ============================= Helpers ==============================

    private fun Response.asJsoup(): Document = org.jsoup.Jsoup.parse(this.body.string(), this.request.url.toString())
}
