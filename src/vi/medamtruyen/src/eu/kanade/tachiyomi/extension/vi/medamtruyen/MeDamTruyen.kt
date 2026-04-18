package eu.kanade.tachiyomi.extension.vi.medamtruyen

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
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class MeDamTruyen : HttpSource() {
    override val name = "MeDamTruyen"
    override val lang = "vi"
    override val baseUrl = "https://metongtai.top"
    override val supportsLatest = true

    private val thumbnailFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = thumbFallbackMap.remove(request.url.toString()) ?: return@Interceptor response

        val isBadCode = response.code == 401 || response.code == 404
        if (!isBadCode) {
            return@Interceptor response
        }

        response.close()

        val fallbackRequest = GET(fallbackUrl, request.headers)
        chain.proceed(fallbackRequest)
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .addInterceptor(thumbnailFallbackInterceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#day-charts .sidebar-comic-block")
            .mapNotNull(::popularMangaFromElement)
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun popularMangaFromElement(element: Element): SManga? {
        val linkElement = element.selectFirst("a.sidebar-comic-block-link[href*=/truyen/]") ?: return null
        val titleElement = element.selectFirst("h3.sidebar-comic-block-title") ?: return null

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst("img.koi-img")?.absUrl("src")
        }
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/truyen-moi-cap-nhat/".toHttpUrl().newBuilder()
            .addQueryParameter("trang", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseBrowsePage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-admin/admin-ajax.php".toHttpUrl().newBuilder().build()
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()

            return POST(url.toString(), headers, formBody)
        }

        val filterList = filters.ifEmpty { getFilterList() }
        val genre = filterList.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        if (genre != null) {
            val url = "$baseUrl/the-loai/$genre/".toHttpUrl().newBuilder()
                .addQueryParameter("trang", page.toString())
                .build()

            return GET(url, headers)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val isAjaxSearch = response.request.url.encodedPath.endsWith("/wp-admin/admin-ajax.php")
        return if (isAjaxSearch) {
            parseSearchApiResponse(response)
        } else {
            parseBrowsePage(response)
        }
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = runCatching {
            response.parseAs<SearchResponse>()
        }.getOrNull() ?: return MangasPage(emptyList(), hasNextPage = false)

        val mangas = searchResponse.data.mapNotNull { result ->
            val mangaLink = result.link?.takeIf { TRUYEN_PATH_REGEX.containsMatchIn(it) } ?: return@mapNotNull null
            val mangaTitle = result.title ?: return@mapNotNull null

            SManga.create().apply {
                title = mangaTitle
                setUrlWithoutDomain(mangaLink.removePrefix(baseUrl))
                thumbnail_url = resolveSearchThumbnailUrl(result.img)
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseBrowsePage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.comic-list-item")
            .mapNotNull(::browseMangaFromElement)
            .distinctBy { it.url }
        val hasNextPage = document.select("a[href*='?trang=']").any { element ->
            element.text().contains("Sau", ignoreCase = true)
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun browseMangaFromElement(element: Element): SManga? {
        val linkElement = element.selectFirst("a.comic-block-link[href*=/truyen/]") ?: return null
        val titleElement = element.selectFirst("h3.comic-block-title") ?: return null

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst("div.comic-block-img img")?.absUrl("src")
        }
    }

    private fun resolveSearchThumbnailUrl(url: String?): String? {
        if (url.isNullOrBlank() || !url.contains(THUMB_LOW_SIZE)) {
            return url
        }

        val removed = url.replace(THUMB_LOW_SIZE, "")
        val replaced = url.replace(THUMB_LOW_SIZE, THUMB_HIGH_SIZE)
        thumbFallbackMap[removed] = replaced
        return removed
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoRows = document.select("div.comic-desc-list ul.list-unstyled li")

        return SManga.create().apply {
            title = document.selectFirst("h2.comic-title")!!.text()
            thumbnail_url = document.selectFirst("div.comic-desc-list meta[itemprop=image]")
                ?.absUrl("content")
                ?.takeIf { it.isNotEmpty() }
                ?: document.selectFirst("div.comic-info-img img, div.chapter-item-img img")?.absUrl("src")
            author = extractInfoValue(infoRows, "Tác giả")
            status = parseStatus(document.selectFirst("span.comic-stt")?.text())
            description = parseDescription(document)
        }
    }

    private fun extractInfoValue(infoRows: List<Element>, label: String): String? = infoRows.firstOrNull { row ->
        row.selectFirst("strong")?.text()?.contains(label, ignoreCase = true) == true
    }?.ownText()?.takeIf { it.isNotBlank() }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("Trọn bộ", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseDescription(document: org.jsoup.nodes.Document): String? {
        val firstParagraph = document.selectFirst("div.hide-long-text p")
            ?.text()
            ?.substringBefore("— Xem Thêm —")
        if (!firstParagraph.isNullOrBlank()) {
            return firstParagraph
        }

        val fullText = document.selectFirst("div.hide-long-text")
            ?.text()
            ?.substringBefore("— Xem Thêm —")
        return fullText?.takeIf { it.isNotBlank() }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapter-list div.chapter-item")
            .mapNotNull(::chapterFromElement)
            .distinctBy { it.url }
    }

    private fun chapterFromElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.chapter-link[href*=-chap-]") ?: return null
        val rawChapterName = element.selectFirst("p.chapter-title")?.text() ?: linkElement.text()

        return SChapter.create().apply {
            setUrlWithoutDomain(linkElement.absUrl("href"))
            name = normalizeChapterName(rawChapterName)
            date_upload = element.selectFirst("p.chapter-meta")
                ?.text()
                ?.let(::parseChapterDate)
                ?: 0L

            CHAPTER_NUMBER_REGEX.find(name)?.value?.replace(",", ".")?.toFloatOrNull()?.let {
                chapter_number = it
            }
        }
    }

    private fun normalizeChapterName(rawChapterName: String): String {
        val chapterMatch = CHAPTER_NAME_REGEX.find(rawChapterName) ?: return rawChapterName
        return chapterMatch.value
            .replace(CHAPTER_WORD_REGEX, "Chap")
            .replace(MULTI_SPACE_REGEX, " ")
    }

    private fun parseChapterDate(chapterMeta: String): Long {
        val dateString = CHAPTER_DATE_REGEX.find(chapterMeta)?.value ?: return 0L
        return CHAPTER_DATE.tryParse(dateString)
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        if (PASSWORD_FORM_REGEX.containsMatchIn(html)) {
            throw Exception(PASSWORD_WEBVIEW_MESSAGE)
        }

        val imageUrls = ImageDecryptor.extractImageUrls(html, response.request.url.toString())
        if (imageUrls.isEmpty()) {
            throw Exception(NO_IMAGES_MESSAGE)
        }

        val chapterUrl = response.request.url.toString()
        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, chapterUrl, imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    companion object {
        private const val THUMB_LOW_SIZE = "-150x150"
        private const val THUMB_HIGH_SIZE = "-720x970"
        private const val PASSWORD_WEBVIEW_MESSAGE = "Vui lòng nhập mật khẩu của chương này qua webview"
        private const val NO_IMAGES_MESSAGE = "Không tìm thấy hình ảnh"

        private val TRUYEN_PATH_REGEX = Regex("""/truyen/""", RegexOption.IGNORE_CASE)
        private val CHAPTER_NAME_REGEX = Regex(
            """chap\s*\d+(?:[.,]\d+)?(?:\s*:\s*.+)?""",
            RegexOption.IGNORE_CASE,
        )
        private val CHAPTER_WORD_REGEX = Regex("""chap""", RegexOption.IGNORE_CASE)
        private val CHAPTER_NUMBER_REGEX = Regex("""\d+(?:[.,]\d+)?""")
        private val CHAPTER_DATE_REGEX = Regex("""\d{2}/\d{2}/\d{2,4}""")
        private val MULTI_SPACE_REGEX = Regex("""\s+""")
        private val PASSWORD_FORM_REGEX = Regex(
            """post-password-form|name=['"]post_password['"]""",
            RegexOption.IGNORE_CASE,
        )
        private val thumbFallbackMap = ConcurrentHashMap<String, String>()

        private val CHAPTER_DATE by lazy {
            SimpleDateFormat("dd/MM/yy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
