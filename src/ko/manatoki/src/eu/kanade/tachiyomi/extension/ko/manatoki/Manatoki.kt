package eu.kanade.tachiyomi.extension.ko.manatoki

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.ByteString.Companion.decodeBase64
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class Manatoki : HttpSource() {

    override val name = "Manatoki"
    override val baseUrl = "https://manatoki552.net"
    override val lang = "ko"
    override val supportsLatest = true
    override val versionId = 2

    // Slow down requests to 2 per second to prevent 403s on image loading
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat by lazy { SimpleDateFormat("yyyy.MM.dd", Locale.ROOT) }

    private val imgScriptRegex = Regex("""var\s+manamoa_img\s*=\s*'([^']+)'""")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = latestUpdatesRequest(page)

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/bbs/board.php?bo_table=cartoon&page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".list-row .list-item").map { element ->
            SManga.create().apply {
                val a = element.selectFirst(".img-item a")
                    ?: throw Exception("Manga URL not found")
                setUrlWithoutDomain(a.attr("abs:href"))
                title = element.selectFirst(".in-lable a font")?.text()
                    ?: a.attr("title").ifEmpty { throw Exception("Manga title not found") }
                thumbnail_url = element.selectFirst(".img-item img")?.attr("abs:src")
            }
        }

        val hasNextPage = document.selectFirst(".pagination li.active + li > a") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/bbs/board.php?bo_table=cartoon&stx=$encodedQuery&page=$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".view-content span b")?.text()
                ?: throw Exception("Title not found")
            thumbnail_url = document.selectFirst(".view-img img")?.attr("abs:src")

            val infoElements = document.select(".view-content")
            for (element in infoElements) {
                val text = element.text()
                if (text.contains("작가 :")) {
                    author = text.substringAfter("작가 :").substringBefore("•").trim()
                } else if (text.contains("분류 :")) {
                    genre = text.substringAfter("분류 :").substringBefore("•").trim()
                }
            }

            status = SManga.UNKNOWN

            initialized = true
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val elements = document.select(".list-body .list-item")
        val total = elements.size

        return elements.mapIndexed { index, element ->
            SChapter.create().apply {
                val a = element.selectFirst(".item-subject")
                    ?: throw Exception("Chapter URL not found")
                setUrlWithoutDomain(a.attr("abs:href"))
                val num = element.selectFirst(".wr-num")?.text()?.toIntOrNull()
                name = if (num != null) {
                    "Chapter $num"
                } else {
                    "Chapter ${total - index}"
                }
                chapter_number = num?.toFloat() ?: (total - index).toFloat()
                val dateStr = element.selectFirst(".wr-date")?.text()
                date_upload = dateFormat.tryParse(dateStr)
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val html = response.use { it.body.string() }

        val match = imgScriptRegex.find(html)

        if (match != null) {
            val base64Str = match.groupValues[1]
            val decodedHtml = base64Str.decodeBase64()?.utf8()
                ?: throw Exception("Failed to decode base64 images")

            val doc = Jsoup.parseBodyFragment(decodedHtml, baseUrl)
            return doc.select("img").mapIndexed { i, img ->
                Page(i, imageUrl = img.attr("abs:src"))
            }
        }

        val doc = Jsoup.parseBodyFragment(html, baseUrl)
        return doc.select(".view-content img").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
