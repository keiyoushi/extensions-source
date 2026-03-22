package eu.kanade.tachiyomi.extension.vi.panomic

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Panomic : HttpSource() {
    override val name = "Panomic"
    override val lang = "vi"
    override val baseUrl = "https://panomic1.info"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    private val searchAjaxUrl = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun Element.lazyImgUrl(): String? = absUrl("data-lazy-src")
        .ifEmpty { absUrl("data-src") }
        .ifEmpty { absUrl("src") }
        .takeUnless { it.isBlank() || it.startsWith("data:") }
        ?.toPreferredThumbnailUrl()

    private fun String.toPreferredThumbnailUrl(): String = replace(THUMB_150_REGEX, "-300x404$1")

    // ========================= Popular ===========================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/nhieu-xem-nhat/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = parseListManga(document.select("ul.most-views.single-list-comic li.position-relative"))
        return MangasPage(mangas, hasNextPage = false)
    }

    // ========================= Latest ============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseLatestPage(response.asJsoup())

    private fun parseLatestPage(document: Document): MangasPage {
        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .filter { element ->
                element
                    .selectFirst(".comic-title-link a[href], .comic-img a[href]")
                    ?.absUrl("href")
                    ?.contains("/truyen/") == true
            }
            .map { element ->
                SManga.create().apply {
                    val linkElement = element.selectFirst(".comic-title-link a[href], .comic-img a[href]")!!
                    title = element.selectFirst("h3.comic-title")!!.text().trim()
                    setUrlWithoutDomain(linkElement.absUrl("href").toRelativeUrl())
                    thumbnail_url = element.selectFirst("img")?.lazyImgUrl()
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

            return POST(
                searchAjaxUrl.toString(),
                headersBuilder()
                    .add("X-Requested-With", "XMLHttpRequest")
                    .build(),
                formBody,
            )
        }

        val filterUri = filters.firstSelectedFilterUri()
        if (filterUri != null) {
            val filterUrl = "$baseUrl/".toHttpUrl().newBuilder()
                .addPathSegments(filterUri)
                .build()
            return GET(filterUrl, headers)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("application/json")) {
            return parseSearchApiResponse(response)
        }

        val document = response.asJsoup()
        val archiveItems = document.select("#archive-list-table li.position-relative")
        if (archiveItems.isNotEmpty()) {
            return parseFilterPage(document, archiveItems)
        }

        val listItems = document.select("ul.single-list-comic li.position-relative")
            .filter { item -> item.selectFirst("p.super-title a[href*='/truyen/']") != null }

        return if (listItems.isNotEmpty()) {
            parseFilterPage(document, listItems)
        } else {
            parseLatestPage(document)
        }
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()

        val mangas = searchResponse.data
            .filter { result -> result.link.contains("/truyen/") }
            .map { result ->
                SManga.create().apply {
                    title = result.title
                    setUrlWithoutDomain(result.link.toRelativeUrl())
                    thumbnail_url = result.img?.toPreferredThumbnailUrl()
                }
            }
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseFilterPage(document: Document, items: List<Element>): MangasPage {
        val mangas = parseListManga(items)
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a[href]:not([href='#'])") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseListManga(items: List<Element>): List<SManga> {
        return items.mapNotNull { element ->
            val linkElement = element.selectFirst("p.super-title a[href]") ?: return@mapNotNull null
            val mangaUrl = linkElement.absUrl("href")
            if (!mangaUrl.contains("/truyen/")) return@mapNotNull null

            SManga.create().apply {
                title = linkElement.text().trim()
                setUrlWithoutDomain(mangaUrl.toRelativeUrl())
                thumbnail_url = element.selectFirst("img.list-left-img, img")?.lazyImgUrl()
            }
        }
    }

    private fun FilterList.firstSelectedFilterUri(): String? = filterIsInstance<UriPartFilter>()
        .map { it.toUriPart() }
        .firstOrNull { it.isNotBlank() }

    private fun String.toRelativeUrl(): String {
        val parsed = toHttpUrlOrNull() ?: return this
        return buildString {
            append(parsed.encodedPath)
            parsed.encodedQuery?.let {
                append('?')
                append(it)
            }
        }
    }

    // ========================= Details ===========================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h2.info-title, .info-title")!!.text()
            thumbnail_url = document.selectFirst("div.col-sm-4 img.img-thumbnail, .detail-info img.img-thumbnail")?.lazyImgUrl()
            author = document.selectFirst("strong:contains(Tác giả) + span")?.text()?.ifEmpty { null }
            status = document.selectFirst("span.comic-stt")?.text()
                ?.let(::parseStatus)
                ?: SManga.UNKNOWN
            genre = document.select("a[href*=/the-loai/]")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst("div.text-justify")?.text()?.ifEmpty { null }
        }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        status.contains("Trọn bộ", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ========================= Chapters ==========================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".table-scroll table tr")
            .mapNotNull { row ->
                val linkElement = row.selectFirst("a.text-capitalize[href], a[href*='-chap-']") ?: return@mapNotNull null

                SChapter.create().apply {
                    val chapterUrl = linkElement.absUrl("href")
                    setUrlWithoutDomain(chapterUrl.toRelativeUrl())
                    name = parseChapterName(linkElement.text(), chapterUrl)
                    date_upload = row.selectFirst("td.hidden-xs.hidden-sm")?.text()
                        ?.let(::parseChapterDate)
                        ?: 0L
                }
            }
    }

    private fun parseChapterName(rawName: String, chapterUrl: String): String {
        val trailingPart = rawName
            .substringAfterLast("–")
            .substringAfterLast("-")
            .trim()

        CHAPTER_NAME_REGEX.find(trailingPart)?.value?.trim()?.let { return it }
        CHAPTER_NAME_REGEX.find(rawName)?.value?.trim()?.let { return it }

        CHAPTER_URL_NUMBER_REGEX.find(chapterUrl)?.groupValues?.getOrNull(1)?.let { chapterNumber ->
            return "Chap $chapterNumber"
        }

        return trailingPart.ifEmpty { rawName.trim() }
    }

    private fun parseChapterDate(dateStr: String): Long {
        val cleanDate = dateStr.trim()
        return DATE_FORMAT_SHORT.tryParse(cleanDate).takeIf { it != 0L }
            ?: DATE_FORMAT_LONG.tryParse(cleanDate)
    }

    // ========================= Pages =============================

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val imageUrls = ImageDecryptor.extractImageUrls(html, response.request.url.toString())

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.distinct().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
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

        private val DATE_FORMAT_LONG by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }

        private val CHAPTER_NAME_REGEX = Regex("Chap\\s*\\d+(\\.\\d+)?", RegexOption.IGNORE_CASE)
        private val CHAPTER_URL_NUMBER_REGEX = Regex("-chap-(\\d+(?:\\.\\d+)?)/?", RegexOption.IGNORE_CASE)
        private val THUMB_150_REGEX = Regex("-150x150(\\.[a-zA-Z0-9]+)$")
    }
}
