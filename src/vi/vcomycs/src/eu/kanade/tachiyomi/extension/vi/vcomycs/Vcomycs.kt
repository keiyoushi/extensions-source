package eu.kanade.tachiyomi.extension.vi.vcomycs

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Vcomycs : HttpSource() {
    override val name = "Vcomycs"
    override val lang = "vi"
    override val baseUrl = "https://vivicomi15.info"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ========================= Popular ===========================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/nhieu-xem-nhat/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.most-views.single-list-comic li.position-relative").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("p.super-title a")!!
                title = linkElement.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("img.list-left-img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ========================= Latest ============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".col-md-3.col-xs-6.comic-item").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("h3.comic-title")!!.parent()!!
                title = element.selectFirst("h3.comic-title")!!.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Search ============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()
            return POST("$baseUrl/wp-admin/admin-ajax.php", headers, formBody)
        }

        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.toUriPart()
        if (!genre.isNullOrBlank()) {
            val url = if (page == 1) "$baseUrl/$genre/" else "$baseUrl/$genre/page/$page/"
            return GET(url, headers)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val contentType = response.header("Content-Type", "")
        if (contentType?.contains("application/json") == true) {
            return parseSearchApiResponse(response)
        }
        return latestUpdatesParse(response)
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()

        val mangas = searchResponse.data
            .filter { it.link.contains("/truyen-tranh/") } // Only manga, exclude news
            .map { result ->
                SManga.create().apply {
                    title = result.title
                    setUrlWithoutDomain(result.link.removePrefix(baseUrl))
                    // Remove -150x150 size suffix from thumbnail URL to get full-size image
                    thumbnail_url = result.img?.replace("-150x150", "")
                }
            }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ========================= Details ===========================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2.info-title")!!.text()
            thumbnail_url = document.selectFirst("img.info-cover")?.absUrl("src")
            author = document.selectFirst("strong:contains(Tác giả) + span")?.text()
            status = document.selectFirst("span.comic-stt")?.text()
                ?.let { parseStatus(it) }
                ?: SManga.UNKNOWN
            genre = document.select("a[href*=/the-loai/]")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst("div.text-justify")?.text()
        }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ========================= Chapters ==========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".table-scroll table tr").mapNotNull { row ->
            val linkElement = row.selectFirst("a.text-capitalize") ?: return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(linkElement.absUrl("href"))
                name = parseChapterName(linkElement.text())
                date_upload = row.selectFirst("td.hidden-xs.hidden-sm")?.text()
                    ?.let { parseChapterDate(it) }
                    ?: 0L
            }
        }
    }

    private fun parseChapterName(rawName: String): String {
        // Extract "Chap X" or "Chap X.Y" pattern from anywhere in the string
        val match = Regex("Chap\\s*\\d+(\\.\\d+)?", RegexOption.IGNORE_CASE).find(rawName)
        return match?.value?.trim() ?: rawName.substringAfterLast("–").substringAfterLast("-").trim()
    }

    private fun parseChapterDate(dateStr: String): Long = DATE_FORMAT_SHORT.tryParse(dateStr)

    // ========================= Pages =============================

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val imageUrls = ImageDecryptor.extractImageUrls(html)

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { idx, url ->
            Page(idx, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = getFilters()

    companion object {
        private val DATE_FORMAT_SHORT by lazy {
            SimpleDateFormat("dd/MM/yy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
