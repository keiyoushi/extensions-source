package eu.kanade.tachiyomi.extension.ru.waymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class WayManga : ParsedHttpSource() {

    override val name = "WayManga"

    override val baseUrl = "https://waymanga.ru"

    override val lang = "ru"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga?page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET(baseUrl, headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search?query=$query")
    }

    override fun popularMangaSelector() = "div.position-relative > a"

    override fun latestUpdatesSelector() = "div.row"

    override fun searchMangaSelector() = "div.col-9 > a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.card-150 > img").first()!!.attr("src")
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first()!!.attr("src")
        element.select("div.col-6 a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("img").first()!!.attr("src")
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = "a.page-link[rel=next]"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        title = document.selectFirst("title")!!.text()
        thumbnail_url = document.selectFirst("img.object-0")!!.attr("src")
    }

    override fun chapterListSelector() = "div.chapters-list > div"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!
        val chapter = SChapter.create()
        element.select("div.col-5").first()!!.let {
            chapter.name = it.text()
            chapter.chapter_number = it.text().substringBefore(" глава").substringAfter("том ").toFloat()
        }
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.date_upload = element.select("div.col-5:eq(1)").text().toDate()
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.ch-show > img").mapIndexed { index, element ->
            Page(index, "", getImage(element))
        }
    }

    private fun getImage(first: Element): String? {
        val image = first.attr("data-src")
        if (image.isNotEmpty()) {
            return image
        }
        return first.attr("src")
    }

    override fun imageUrlParse(document: Document) = ""

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMATTER.parse(trim())?. time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    }
}
