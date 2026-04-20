package eu.kanade.tachiyomi.extension.vi.soaicacomic

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class SoaiCaComic : HttpSource() {

    override val name = "SoaiCaComic"
    override val lang = "vi"
    override val supportsLatest = true

    override val baseUrl = "https://soaicacomic2.top"

    private val thumbnailFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = thumbFallbackMap.remove(request.url.toString()) ?: return@Interceptor response

        if (response.code != 401 && response.code != 404) {
            return@Interceptor response
        }

        response.close()
        chain.proceed(GET(fallbackUrl, request.headers))
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .addInterceptor(thumbnailFallbackInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/xem-nhieu-nhat/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup()
            .select("ul.most-views.single-list-comic li.position-relative")
            .mapNotNull(::archiveMangaFromElement)

        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .mapNotNull(::latestMangaFromElement)
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun latestMangaFromElement(element: org.jsoup.nodes.Element): SManga? {
        val linkElement = element.selectFirst(".comic-img a[href], .comic-title-link > a[href]") ?: return null
        if (!linkElement.absUrl("href").contains("/truyen-tranh/")) {
            return null
        }

        return SManga.create().apply {
            title = element.selectFirst("h3.comic-title")!!.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst(".comic-img img, img.img-thumbnail")?.absUrl("src")
        }
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()
            return POST("$baseUrl/wp-admin/admin-ajax.php", headers, formBody)
        }

        val filterPath = selectedFilterPath(filters)
        if (filterPath != null) {
            val url = "$baseUrl/$filterPath/".toHttpUrl().newBuilder()
                .addQueryParameter("tachiyomi-page", page.toString())
                .build()
            return GET(url, headers)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("application/json")) {
            return parseSearchApiResponse(response)
        }

        val document = response.asJsoup()
        val archivePage = response.request.url.queryParameter("tachiyomi-page")
            ?.toIntOrNull()

        if (archivePage != null) {
            return if (document.selectFirst("#archive-list-table") != null) {
                parseArchivePage(document, archivePage)
            } else {
                parseFilterFallbackPage(document)
            }
        }

        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .mapNotNull(::latestMangaFromElement)
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()

        val mangas = searchResponse.data
            .filter { it.link.contains("/truyen-tranh/") }
            .mapNotNull { result ->
                val path = result.link.toHttpUrlOrNull()?.encodedPath ?: return@mapNotNull null
                SManga.create().apply {
                    title = result.title
                    setUrlWithoutDomain(path)
                    thumbnail_url = resolveSearchThumbnailUrl(result.imageUrl())
                }
            }
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseArchivePage(document: org.jsoup.nodes.Document, page: Int): MangasPage {
        val mangas = document.select("#archive-list-table > li.position-relative")
            .mapNotNull(::archiveMangaFromElement)
            .distinctBy { it.url }

        val fromIndex = ((page - 1).coerceAtLeast(0)) * 32
        if (fromIndex >= mangas.size) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val pageItems = mangas.drop(fromIndex).take(32)
        val hasNextPage = mangas.size > fromIndex + pageItems.size

        return MangasPage(pageItems, hasNextPage)
    }

    private fun parseFilterFallbackPage(document: org.jsoup.nodes.Document): MangasPage {
        val archiveItems = document.select("ul.most-views.single-list-comic li.position-relative")
            .mapNotNull(::archiveMangaFromElement)
        if (archiveItems.isNotEmpty()) {
            return MangasPage(archiveItems, hasNextPage = false)
        }

        val latestItems = document.select(".col-md-3.col-xs-6.comic-item")
            .mapNotNull(::latestMangaFromElement)
        return MangasPage(latestItems, hasNextPage = false)
    }

    private fun resolveSearchThumbnailUrl(url: String?): String? {
        if (url.isNullOrBlank() || !url.contains("-150x150")) return url

        val removed = url.replace("-150x150", "")
        val replaced = url.replace("-150x150", "-720x970")
        thumbFallbackMap[removed] = replaced
        return removed
    }

    private fun archiveMangaFromElement(element: org.jsoup.nodes.Element): SManga? {
        val linkElement = element.selectFirst("p.super-title a[href]") ?: return null
        val url = linkElement.absUrl("href")
        if (!url.contains("/truyen-tranh/")) {
            return null
        }

        return SManga.create().apply {
            title = linkElement.text()
            setUrlWithoutDomain(url)
            thumbnail_url = element.selectFirst("img.list-left-img")?.absUrl("src")
        }
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h2.info-title, .info-title")!!.text()
            thumbnail_url = document.selectFirst(".comic-intro img.img-thumbnail")?.absUrl("src")
            author = document.selectFirst("strong:contains(Tác giả) + span")?.text()
            status = document.selectFirst("span.comic-stt")?.text()
                ?.let(::parseStatus)
                ?: SManga.UNKNOWN
            genre = document.select(".comic-info .tags a[href*=/the-loai/]")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = parseDescription(document)
        }
    }

    private fun parseDescription(document: org.jsoup.nodes.Document): String? {
        val block = document.selectFirst(".intro-container .hide-long-text")
            ?: document.selectFirst(".intro-container > p")
            ?: return null

        val description = block.text()
            .substringBefore("— Xem Thêm —")
            .substringBefore("- Xem thêm -")
            .removePrefix("\"")
            .removeSuffix("\"")

        return description.takeUnless {
            it.isEmpty() ||
                it.equals("Đang cập nhật", ignoreCase = true) ||
                it.equals("Đang cập nhật...", ignoreCase = true) ||
                it.equals("Không có", ignoreCase = true)
        }
    }

    private fun parseStatus(status: String): Int = when {
        status.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) || status.contains("Trọn bộ", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".chapter-table table tbody tr")
        .mapNotNull(::chapterFromElement)

    private fun chapterFromElement(element: org.jsoup.nodes.Element): SChapter? {
        val linkElement = element.selectFirst("a.text-capitalize[href]") ?: return null
        val dateText = element.selectFirst("td.hidden-xs.hidden-sm, td:last-child")?.text() ?: return null
        val dateUpload = parseChapterDate(dateText)
        if (dateUpload == 0L) {
            return null
        }

        val fullText = linkElement.selectFirst("span.hidden-sm.hidden-xs")?.text() ?: linkElement.text()
        val chapterName = parseChapterName(fullText)
        val isLocked = linkElement.selectFirst(".glyphicon-lock, .fa-lock, .icon-lock") != null ||
            element.selectFirst(".glyphicon-lock, .fa-lock, .icon-lock") != null

        return SChapter.create().apply {
            setUrlWithoutDomain(linkElement.absUrl("href"))
            name = if (isLocked) "🔒 $chapterName" else chapterName
            date_upload = dateUpload
        }
    }

    private fun parseChapterName(rawName: String): String {
        val match = CHAPTER_NAME_REGEX.find(rawName)
        if (match != null) {
            return match.value
                .replace(CHAPTER_WORD_REGEX, "CHAP")
                .replace(MULTI_SPACE_REGEX, " ")
        }

        return rawName.substringAfterLast("–").substringAfterLast("-")
    }

    private fun parseChapterDate(dateText: String): Long = DATE_FORMAT.tryParse(dateText)

    // ============================== Filter =================================

    override fun getFilterList(): FilterList = getFilters()

    private fun selectedFilterPath(filters: FilterList): String? = filters.firstInstanceOrNull<TheLoaiFilter>()?.toUriPart()?.ifEmpty { null }
        ?: filters.firstInstanceOrNull<NhomFilter>()?.toUriPart()?.ifEmpty { null }
        ?: filters.firstInstanceOrNull<LoatTruyenFilter>()?.toUriPart()?.ifEmpty { null }
        ?: filters.firstInstanceOrNull<TuKhoaFilter>()?.toUriPart()?.ifEmpty { null }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        if (PASSWORD_FIELD_REGEX.containsMatchIn(html)) {
            throw Exception(PASSWORD_WEBVIEW_MESSAGE)
        }

        val imageUrls = ImageDecryptor.extractImageUrls(html, response.request.url.toString())
        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val PASSWORD_WEBVIEW_MESSAGE = "Vui lòng nhập mật khẩu của chương này qua webview"

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }

        private val CHAPTER_NAME_REGEX = Regex("chap\\s*\\d+(?:\\.\\d+)?", RegexOption.IGNORE_CASE)
        private val CHAPTER_WORD_REGEX = Regex("chap", RegexOption.IGNORE_CASE)
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val PASSWORD_FIELD_REGEX = Regex("name=\\\"post_password\\\"|name=post_password")

        private val thumbFallbackMap = ConcurrentHashMap<String, String>()
    }
}
