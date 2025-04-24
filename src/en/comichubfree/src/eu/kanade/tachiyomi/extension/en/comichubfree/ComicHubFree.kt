package eu.kanade.tachiyomi.extension.en.comichubfree

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ComicHubFree : ParsedHttpSource() {
    private val dateFormat = SimpleDateFormat("d-MMM-yyyy", Locale.getDefault())

    override val baseUrl = "https://comichubfree.com"

    override val lang = "en"

    override val name = "ComicHubFree"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = ".movie-list-index > .cartoon-box:has(.detail)"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector() = "ul.pagination a[rel=next]:not(hidden)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun chapterListSelector() = "div.episode-list > div > table > tbody > tr"

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        fun parseChapters(document: Document) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            document.selectFirst(popularMangaNextPageSelector())?.let { it ->
                parseChapters(client.newCall(GET(it.absUrl("href"), headers)).execute().asJsoup())
            }
        }

        parseChapters(response.asJsoup())

        return chapters
    }

    override fun imageUrlParse(document: Document) = ""

    private fun Element.imageAttr(): String {
        return when {
            hasAttr("data-src") -> absUrl("data-src")
            else -> absUrl("src")
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/popular-comic".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/new-comic".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search-comic".toHttpUrl().newBuilder().apply {
            addQueryParameter("key", query)
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = (baseUrl + chapter.url + "/all").toHttpUrl()

        return GET(url, headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            title = element.selectFirst("h3")!!.text()
            val image = element.selectFirst("img")
            thumbnail_url = image?.imageAttr()
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.selectFirst("a")!!
        val dateElement = element.select("td:last-of-type")

        val chapter = SChapter.create().apply {
            setUrlWithoutDomain(urlElement.attr("href"))
            name = urlElement.text()
            date_upload = dateFormat.tryParse(dateElement.text())
        }

        return chapter
    }

    private fun parseStatus(status: String): Int {
        return when (status) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.selectFirst("div.movie-info")!!
        val seriesInfoElement = infoElement.selectFirst("div.series-info")
        val seriesDescriptionElement = infoElement.selectFirst("div#film-content")

        val authorElement = seriesInfoElement?.select("dt:contains(Authors:) + dd")
        val statusElement = seriesInfoElement?.select("dt:contains(Status:) + dd")

        val image = seriesInfoElement?.selectFirst("img")

        val manga = SManga.create().apply {
            description = seriesDescriptionElement?.text()
            thumbnail_url = image?.imageAttr()
            author = authorElement?.text()
            status = statusElement?.text().orEmpty().let { parseStatus(it) }
        }

        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.chapter_img").mapIndexed { index, element ->
            val img = element.imageAttr()
            Page(index, imageUrl = img)
        }.distinctBy { it.imageUrl }
    }
}
