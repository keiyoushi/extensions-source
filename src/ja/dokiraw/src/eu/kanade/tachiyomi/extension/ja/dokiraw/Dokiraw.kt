package eu.kanade.tachiyomi.extension.ja.dokiraw

import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

class Dokiraw : Liliana("Dokiraw", "https://dokiraw.run", "ja") {

    override val supportsLatest = false

    private val dateRegex = Regex("""(\d+)""")

    // =============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/hot", headers)
    override fun popularMangaSelector(): String = "div[class*=manga-item_item]"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val anchor = element.selectFirst("a[href*=/manga/]")!!
        setUrlWithoutDomain(anchor.attr("abs:href"))

        title = element.select("h3").text()

        val thumbnail = element.selectFirst("img")!!

        thumbnail_url = thumbnail
            .attr("abs:data-original")
            .ifEmpty {
                thumbnail.attr("abs:src")
            }
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/manga"
            .toHttpUrl()
            .newBuilder()
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("keyword", query)
                }
                addQueryParameter("page", page.toString())
            }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div[class*=manga-detail_boxInfo] h1")!!.text()

        description = document.select("div.work-break p").lastOrNull()?.text()

        status = when {
            document.selectFirst("span:contains(連載中)") != null -> SManga.ONGOING
            document.selectFirst("span:contains(完結)") != null -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.selectFirst("img[src*=cover]")?.attr("abs:src")
    }

    // ============================== Chapters ==============================

    override fun chapterListSelector() = "a:has(div[class*=manga-detail_chapter])"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val container = element.selectFirst("div[class*=manga-detail_chapter]")!!
        val nameAndDate = container.select("span")

        name = nameAndDate.first()!!.text()

        setUrlWithoutDomain(element.attr("abs:href"))

        // Convert japanese dates to a Long (1月前 -> 2.6e+9)
        val dateText = nameAndDate.getOrNull(1)?.text()
        date_upload = parseDate(dateText)
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    override fun pageListParse(document: Document): List<Page> {
        val imageElements = document.select("div.page-chapter img")

        return imageElements.mapIndexed { index, element ->

            val dataCdn = element.attr("abs:data-cdn")
            val dataOriginal = element.attr("abs:data-original")
            val src = element.attr("abs:src")

            val finalUrl = when {
                dataCdn.isNotBlank() -> dataCdn
                dataOriginal.isNotBlank() -> dataOriginal
                else -> src
            }

            Page(index, "", finalUrl)
        }
    }

    // ============================= Utilities ==============================

    private fun parseDate(dateStr: String?): Long {
        dateStr ?: return 0L

        val now = Calendar.getInstance()

        // Special case for "Yesterday"
        if ("昨日" in dateStr) {
            now.add(Calendar.DAY_OF_MONTH, -1)
            return now.timeInMillis
        }

        val amount = dateRegex.find(dateStr)?.groupValues?.get(1)?.toIntOrNull() ?: return 0L

        when {
            "秒" in dateStr -> now.add(Calendar.SECOND, -amount)
            "分" in dateStr -> now.add(Calendar.MINUTE, -amount)
            "時" in dateStr -> now.add(Calendar.HOUR, -amount)
            "日" in dateStr -> now.add(Calendar.DAY_OF_MONTH, -amount)
            "週" in dateStr -> now.add(Calendar.DAY_OF_MONTH, -amount * 7)
            "月" in dateStr -> now.add(Calendar.MONTH, -amount)
            "年" in dateStr -> now.add(Calendar.YEAR, -amount)
            else -> return 0L
        }
        return now.timeInMillis
    }
}
