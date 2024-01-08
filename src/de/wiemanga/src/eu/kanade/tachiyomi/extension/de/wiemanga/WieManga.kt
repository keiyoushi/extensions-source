package eu.kanade.tachiyomi.extension.de.wiemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class WieManga : ParsedHttpSource() {

    override val id: Long = 10

    override val name = "Wie Manga!"

    override val baseUrl = "https://www.wiemanga.com"

    override val lang = "de"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept-Language", "en-US,en;q=0.5")
        .add("Referer", baseUrl)

    override fun popularMangaSelector() = ".booklist td > div"

    override fun latestUpdatesSelector() = ".booklist td > div"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/list/Hot-Book/", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/list/New-Update/", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("dd a:first-child").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("dt img").attr("abs:src")

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/?wd=$query", headers)
    }

    override fun searchMangaSelector() = ".searchresult td > div"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select(".resultbookname").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select(".resultimg img").attr("abs:src")

        return manga
    }

    override fun searchMangaNextPageSelector() = ".pagetor a.l"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        document.select("div.bookmessgae").let { details ->
            manga.author = details.select("dd:contains(Autor:) a").text()
            manga.artist = details.select("dd:contains(Zeichner:) a").text()
            manga.genre = details.select("dd:contains(Genre:) a").joinToString { it.text() }
            manga.description = details.select("dt").first()?.ownText()
            manga.thumbnail_url = details.select("div.bookfrontpage img").attr("abs:src")
            manga.status = parseStatus(details.select("dd:contains(Status:) a").text())
        }

        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("finished", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".chapterlist tr:not(:first-child)"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select(".col1 a").first()!!.let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.text()
        }
        chapter.date_upload = element.select(".col3 a").first()?.text()?.let { parseChapterDate(it) } ?: 0

        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault()).parse(date)?.time ?: 0L
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("select#page").first()!!.select("option").forEach {
            pages.add(Page(pages.size, it.attr("value")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String {
        return document.select("img#comicpic").first()!!.attr("abs:src")
    }
}
