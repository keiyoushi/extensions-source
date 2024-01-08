package eu.kanade.tachiyomi.extension.en.clonemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class CloneManga : ParsedHttpSource() {

    override val name = "Clone Manga"
    override val baseUrl = "https://manga.clone-army.org/"
    override val lang = "en"
    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/viewer_landing.php")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        // Gets every manga on landing page
        val document = response.asJsoup()
        val mangas = document.getElementsByClass(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    override fun popularMangaSelector(): String {
        return "comicPreviewContainer"
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val attr = element.getElementsByClass("comicPreview").attr("style")
        return SManga.create().apply {
            title = element.select("h3").first()!!.text()
            artist = "Dan Kim"
            author = artist
            status = SManga.UNKNOWN
            url = element.select("a").first()!!.attr("href")
            description = element.select("h4").first()?.text() ?: ""
            thumbnail_url = baseUrl + attr.substring(
                attr.indexOf("site/themes"),
                attr.indexOf(")"),
            )
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = fetchPopularManga(1)
        .map { mp -> MangasPage(mp.mangas.filter { it.title.contains(query, ignoreCase = true) }, false) }

    override fun mangaDetailsParse(document: Document): SManga {
        // Populate with already fetched details
        return SManga.create()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // Treat each page as an individual chapter
        val document = response.asJsoup()
        val series = document.location()
        val numChapters = Regex(
            pattern = "&page=(.*)&lang=",
        ).findAll(
            input = document.getElementsByTag("script")[3].toString(),
        )
            .elementAt(3).destructured.component1()
            .toInt()
        val chapters = ArrayList<SChapter>()

        for (i in 1..numChapters) {
            val chapter = SChapter.create().apply {
                url = "$series&page=$i"
                name = "Chapter $i"
                date_upload = 0
                chapter_number = i.toFloat()
            }
            chapters.add(chapter)
        }
        return chapters.reversed() // Reverse to correct ordering
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(chapter.url)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imgAbsoluteUrl = document.getElementsByClass("subsectionContainer")[0]
            .select("img").first()!!.absUrl("src")
        // List of pages will always contain only one page
        return listOf(Page(1, "", imgAbsoluteUrl))
    }

    override fun imageUrlParse(document: Document): String { throw Exception("Not used") }

    override fun pageListParse(document: Document): List<Page> { throw Exception("Not used") }

    override fun chapterListSelector(): String { throw Exception("Not used") }

    override fun chapterFromElement(element: Element): SChapter { throw Exception("Not used") }

    override fun latestUpdatesFromElement(element: Element): SManga { throw Exception("Not used") }

    override fun latestUpdatesNextPageSelector(): String? { throw Exception("Not used") }

    override fun latestUpdatesRequest(page: Int): Request { throw Exception("Not used") }

    override fun latestUpdatesSelector(): String { throw Exception("Not used") }

    override fun popularMangaNextPageSelector(): String? { throw Exception("Not used") }

    override fun searchMangaFromElement(element: Element): SManga { throw Exception("Not used") }

    override fun searchMangaNextPageSelector(): String? { throw Exception("Not used") }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request { throw Exception("Not used") }

    override fun searchMangaSelector(): String { throw Exception("Not used") }
}
