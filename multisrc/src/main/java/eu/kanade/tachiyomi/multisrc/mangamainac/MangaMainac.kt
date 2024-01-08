package eu.kanade.tachiyomi.multisrc.mangamainac

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

// Based On TCBScans sources
// MangaManiac is a network of sites built by Animemaniac.co.

abstract class MangaMainac(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {
    override val supportsLatest = false
    override val client: OkHttpClient = network.cloudflareClient

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl)
    }

    override fun popularMangaSelector() = "#page"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select(".mangainfo_body > img").attr("src")
        manga.url = "" // element.select("#primary-menu .menu-item:first-child").attr("href")
        manga.title = element.select(".intro_content h2").text()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // latest
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesSelector(): String = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector(): String? = throw UnsupportedOperationException()

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun searchMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    // manga details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val info = document.select(".intro_content").text()
        thumbnail_url = document.select(".mangainfo_body > img").attr("src")
        title = document.select(".intro_content h2").text()
        author = if ("Author" in info) substringextract(info, "Author(s):", "Released") else null
        artist = author
        genre = if ("Genre" in info) substringextract(info, "Genre(s):", "Status") else null
        status = parseStatus(document.select(".intro_content").text())
        description = if ("Description" in info) info.substringAfter("Description:").trim() else null
    }

    private fun substringextract(text: String, start: String, end: String): String = text.substringAfter(start).substringBefore(end).trim()

    private fun parseStatus(element: String): Int = when {
        element.lowercase().contains("ongoing (pub") -> SManga.ONGOING
        element.lowercase().contains("completed (pub") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapters
    override fun chapterListSelector() = "table.chap_tab tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()!!
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = element.select("a").text()
        chapter.date_upload = element.select("#time i").last()?.text()?.let { parseChapterDate(it) }
            ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords: List<String> = date.split(" ")
        if (dateWords.size == 3) {
            val timeAgo = Integer.parseInt(dateWords[0])
            val dates: Calendar = Calendar.getInstance()
            when {
                dateWords[1].contains("minute") -> {
                    dates.add(Calendar.MINUTE, -timeAgo)
                }
                dateWords[1].contains("hour") -> {
                    dates.add(Calendar.HOUR_OF_DAY, -timeAgo)
                }
                dateWords[1].contains("day") -> {
                    dates.add(Calendar.DAY_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("week") -> {
                    dates.add(Calendar.WEEK_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("month") -> {
                    dates.add(Calendar.MONTH, -timeAgo)
                }
                dateWords[1].contains("year") -> {
                    dates.add(Calendar.YEAR, -timeAgo)
                }
            }
            return dates.timeInMillis
        }
        return 0L
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterList = document.select(chapterListSelector()).map { chapterFromElement(it) }

        return if (hasCountdown(chapterList[0])) {
            chapterList.subList(1, chapterList.size)
        } else {
            chapterList
        }
    }

    private fun hasCountdown(chapter: SChapter): Boolean {
        val document = client.newCall(
            GET(
                baseUrl + chapter.url,
                headersBuilder().build(),
            ),
        ).execute().asJsoup()

        return document
            .select("iframe[src^=https://free.timeanddate.com/countdown/]")
            .isNotEmpty()
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select(".container .img_container center img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""
}
