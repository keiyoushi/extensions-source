package eu.kanade.tachiyomi.extension.ja.rawxz

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import java.util.Calendar

class RawXZ : HttpSource() {
    override val name = "RawZO"
    override val baseUrl = "https://rawzo.net"
    override val lang = "ja"
    override val supportsLatest = true

    override val id = 7950551186567193810L

    // Popular Manga
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?orderby=views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".manga-card").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".manga-card-title")!!.text()
                setUrlWithoutDomain(element.selectFirst("a.manga-card-thumb")!!.attr("href"))
                thumbnail_url = element.selectFirst(".manga-card-thumb img")?.absUrl("src")
            }
        }

        val hasNextPage = if (response.request.url.queryParameter("s") != null) {
            mangas.size >= 40
        } else {
            document.selectFirst(".pagination a:has(i.fa-chevron-right)") != null
        }

        return MangasPage(mangas, hasNextPage)
    }

    // Latest Updates
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?orderby=date", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("s", query)
            addQueryParameter("post_type", "manga")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // Manga Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".md-title")!!.text()
            author = document.select(".md-meta-row:has(.fa-user) .md-meta-val").text().takeIf { it != "更新中" }
            status = parseStatus(document.select(".md-meta-row:has(.fa-rss) .md-meta-val").text())
            genre = document.select(".md-tag").joinToString { it.text() }
            description = document.selectFirst(".md-desc-content")?.text()
            thumbnail_url = document.selectFirst(".md-cover img")?.absUrl("src")
        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("連載中") -> SManga.ONGOING
        status.contains("完結") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapter List
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".md-chapter-row").map { element ->
            SChapter.create().apply {
                val link = element.selectFirst(".md-chapter-name a")!!
                name = link.ownText()
                setUrlWithoutDomain(link.attr("href"))
                date_upload = parseRelativeDate(element.selectFirst(".md-chapter-time")?.text())
            }
        }
    }

    // Page List
    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            throw Exception("この章のURLは古くなっています。マンガを更新してください。")
        }

        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".reader-page img").mapIndexed { idx, image ->
            val url = image.absUrl("src")
            val imageUrl = if (url.contains("img-proxy.php?url=")) {
                URLDecoder.decode(url.substringAfter("img-proxy.php?url="), "UTF-8")
            } else {
                url
            }

            Page(idx, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun parseRelativeDate(date: String?): Long {
        if (date == null) return 0L

        val calendar = Calendar.getInstance()
        val amount = date.split(" ")[0].filter { it.isDigit() }.toIntOrNull() ?: return 0L

        when {
            date.contains("秒前") -> calendar.add(Calendar.SECOND, -amount)
            date.contains("分前") -> calendar.add(Calendar.MINUTE, -amount)
            date.contains("時間前") -> calendar.add(Calendar.HOUR, -amount)
            date.contains("日前") -> calendar.add(Calendar.DATE, -amount)
            date.contains("週間前") -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
            date.contains("ヶ月前") -> calendar.add(Calendar.MONTH, -amount)
            date.contains("年前") -> calendar.add(Calendar.YEAR, -amount)
            else -> return 0L
        }

        return calendar.timeInMillis
    }
}
