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

class Dokiraw : Liliana("Dokiraw", "https://dokiraw.club", "ja") {

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/hot", headers)
    override fun popularMangaSelector(): String = "div[class*=manga-item_item]"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val anchor = element.selectFirst("a[href*=/manga/]")
        setUrlWithoutDomain(anchor?.attr("abs:href") ?: "")

        title = element.select("h3").text()

        thumbnail_url = element
            .select("img")
            .attr("abs:data-original")
            .ifEmpty {
                element
                    .select("img")
                    .attr("abs:src")
            }
    }

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

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title =
            document.selectFirst("div[class*=manga-detail_boxInfo] h1")?.text() ?: "Unknown Title"

        description = document.select("div.work-break p").lastOrNull()?.text()

        status = when {
            document.select("span:contains(連載中)").isNotEmpty() -> SManga.ONGOING
            document.select("span:contains(完結)").isNotEmpty() -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.select("img[src*=cover]").attr("abs:src")
    }

    override fun chapterListSelector() = "a:has(div[class*=manga-detail_chapter])"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val container = element.selectFirst("div[class*=manga-detail_chapter]")

        name = container?.selectFirst("span.font-bold")?.text() ?: "Unknown Chapter"

        setUrlWithoutDomain(element.attr("href"))

        // Parse japanese dates to a Long (1月前 -> 2.6e+9)
        val dateText = container?.selectFirst("span.text-gray-300")?.text() ?: ""
        date_upload = parseDate(dateText)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = baseUrl + chapter.url
        return GET(chapterUrl, headers)
    }

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

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        headersBuilder().apply {
            add("Referer", baseUrl)
        }.build(),
    )

    private fun parseDate(dateStr: String): Long {
        val now = java.util.Calendar.getInstance()

        val amount =
            Regex("""(\d+)""").find(dateStr)?.groupValues?.get(1)?.toIntOrNull() ?: return 0L

        when {
            "秒" in dateStr -> now.add(java.util.Calendar.SECOND, -amount)
            "分" in dateStr -> now.add(java.util.Calendar.MINUTE, -amount)
            "時" in dateStr -> now.add(java.util.Calendar.HOUR, -amount)
            "日" in dateStr -> now.add(java.util.Calendar.DAY_OF_MONTH, -amount)
            "月" in dateStr -> now.add(java.util.Calendar.MONTH, -amount)
            "年" in dateStr -> now.add(java.util.Calendar.YEAR, -amount)
            else -> return 0L
        }
        return now.timeInMillis
    }
}
