package eu.kanade.tachiyomi.extension.ar.shqqaa

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Shqqaa : ParsedHttpSource() {

    override val name = "مانجا شقاع"

    override val baseUrl = "https://www.shqqaa.com"

    override val lang = "ar"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga", headers)
    }

    override fun popularMangaSelector() = "div.card"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first()!!.attr("data-src")
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title").split(", ")[0]
        }
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/chapters/", headers)
    }

    override fun latestUpdatesSelector(): String = "div.row > div.col-xl-3"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain("${it.attr("href").substringBeforeLast('/')}/")
            manga.title = element.select("small").first()!!.text().split(", ")[0]
        }
        manga.thumbnail_url = element.select("img").first()!!.attr("data-src")
        return manga
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(1)
        .map { mp -> MangasPage(mp.mangas.filter { it.title.contains(query, ignoreCase = true) }, false) }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not Used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not Used")

    override fun searchMangaNextPageSelector() = throw Exception("Not Used")

    // Manga summary page
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.col-sm-12")
        val mangaInfo = infoElement[1]
        val manga = SManga.create()
        manga.title = mangaInfo.select("small.text-muted")[1].ownText().split(", ")[0]
        manga.author = null
        val status = mangaInfo.select("span.badge").first()!!.ownText()
        manga.status = parseStatus(status)
        manga.genre = null
        manga.description = infoElement.first()!!.select(".text-muted").first()!!.ownText()
        manga.thumbnail_url = mangaInfo.select("img").attr("data-src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("مستمر") -> SManga.ONGOING
        status.contains("منتهي") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters
    override fun chapterListSelector() = "a.m-1"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("a").let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.text()
        }
        chapter.date_upload = 0
        return chapter
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("div.img-manga img").forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }
        return pages
    }
    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
