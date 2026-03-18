package eu.kanade.tachiyomi.extension.vi.teamlanhlung

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
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class TeamLanhLung : HttpSource() {

    override val name: String = "Team Lạnh Lùng"

    override val baseUrl: String = "https://hongtruyentranh.com"

    override val lang: String = "vi"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun Element.lazyImgUrl(): String? {
        val url = absUrl("data-lazy-src")
            .ifEmpty { absUrl("data-src") }
            .ifEmpty { absUrl("src").takeUnless { it.startsWith("data:") } }
            ?.ifEmpty { null }
            ?: return null
        return url.replace(SMALL_THUMBNAIL_REGEX, "$1")
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/xem-nhieu-nhat/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("ul.most-views.single-list-comic li.position-relative")
            .map(::mangaFromListItem)
        return MangasPage(mangas, hasNextPage = false)
    }

    private fun mangaFromListItem(element: Element): SManga {
        val linkElement = element.selectFirst("p.super-title a[href]")!!
        return SManga.create().apply {
            title = linkElement.text().trim()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst("img.list-left-img, img")?.lazyImgUrl()
        }
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseLatestPage(response.asJsoup())

    private fun parseLatestPage(document: Document): MangasPage {
        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .filter { element ->
                val href = element.selectFirst("a[href]")?.absUrl("href").orEmpty()
                href.contains("/truyen-tranh/")
            }
            .map { element ->
                SManga.create().apply {
                    val titleElement = element.selectFirst("h3.comic-title")!!
                    title = titleElement.text()
                    setUrlWithoutDomain(titleElement.parent()!!.absUrl("href"))
                    thumbnail_url = element.selectFirst("img")?.lazyImgUrl()
                }
            }

        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

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
            return GET("$baseUrl/$genre/", headers)
        }

        return latestUpdatesRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("application/json")) {
            return parseSearchApiResponse(response)
        }

        val document = response.asJsoup()
        val requestPath = response.request.url.encodedPath

        if (requestPath.contains("/the-loai/")) {
            val items = document.select("#archive-list-table li.position-relative")
                .ifEmpty { document.select("ul.single-list-comic li.position-relative") }
            val mangas = items.map(::mangaFromListItem)
            return MangasPage(mangas, false)
        }

        val latestItems = document.select(".col-md-3.col-xs-6.comic-item")
        if (latestItems.isNotEmpty()) {
            return parseLatestPage(document)
        }

        return MangasPage(emptyList(), false)
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponseDto>()

        if (!searchResponse.success) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val mangas = searchResponse.data
            .mapNotNull { result ->
                val title = result.title ?: return@mapNotNull null
                val link = result.link ?: return@mapNotNull null
                if (!link.contains("/truyen-tranh/")) {
                    return@mapNotNull null
                }

                SManga.create().apply {
                    this.title = title
                    setUrlWithoutDomain(link.removePrefix(baseUrl))
                    thumbnail_url = result.img?.replace(SMALL_THUMBNAIL_REGEX, "$1")
                }
            }
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h2.info-title")!!.text()
            thumbnail_url = document.selectFirst(".img-thumbnail")?.lazyImgUrl()
            author = document.selectFirst("strong:contains(Tác giả) + span")
                ?.text()
                ?.takeUnless { it.equals("Đang cập nhật", true) || it.equals("Không có", true) }
            description = parseDescription(document)
            genre = document.select(".comic-info .tags a[href*='/the-loai/']")
                .joinToString { it.text() }
                .ifEmpty { null }

            val statusString = document.selectFirst("span.comic-stt")?.text()
            status = when {
                statusString?.contains("Đang tiến hành", ignoreCase = true) == true -> SManga.ONGOING
                statusString?.contains("Trọn bộ", ignoreCase = true) == true -> SManga.COMPLETED
                statusString?.contains("Hoàn thành", ignoreCase = true) == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun parseDescription(document: Document): String? {
        val block: Element = document.selectFirst(".intro-container .hide-long-text") ?: return null
        val ownText = block.ownText().trim()
        val rawDescription = (if (ownText.isNotEmpty()) ownText else block.text())
            .substringBefore("— Xem Thêm —")
            .trim()

        return rawDescription
            .removePrefix("\"")
            .removeSuffix("\"")
            .trim()
            .ifEmpty { null }
    }

    // ============================== Chapters ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()

        chapters += document.select(".chapter-table table tbody tr").mapNotNull(::parseChapterElement)

        val visited = mutableSetOf(response.request.url.toString())
        var nextPage = document.selectFirst("ul.pager li.next:not(.disabled) a")
            ?.absUrl("href")
            ?.ifEmpty { null }

        while (nextPage != null && visited.add(nextPage)) {
            client.newCall(GET(nextPage, headers)).execute().use { pageResponse ->
                document = pageResponse.asJsoup()
                chapters += document.select(".chapter-table table tbody tr").mapNotNull(::parseChapterElement)
                nextPage = document.selectFirst("ul.pager li.next:not(.disabled) a")
                    ?.absUrl("href")
                    ?.ifEmpty { null }
            }
        }

        return chapters
    }

    private fun parseChapterElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.text-capitalize") ?: return null
        val url = linkElement.absUrl("href")
        if (url.isEmpty()) return null

        val isLocked = linkElement.selectFirst(".glyphicon-lock, .fa-lock, .icon-lock") != null

        return SChapter.create().apply {
            setUrlWithoutDomain(url)
            val fullText = linkElement.selectFirst("span.hidden-sm.hidden-xs")?.text()
                ?: linkElement.text()
            val shortName = parseChapterName(fullText)
            name = if (isLocked) "🔒 $shortName" else shortName

            date_upload = element.selectFirst("td.hidden-xs.hidden-sm, td:last-child")
                ?.text()
                ?.let { parseChapterDate(it) }
                ?: 0L
        }
    }

    private fun parseChapterName(rawName: String): String {
        val match = CHAPTER_NAME_REGEX.find(rawName)
        if (match != null) {
            return match.value
                .replace(CHAPTER_WORD_REGEX, "CHAP")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
        }

        return rawName.substringAfterLast("–").substringAfterLast("-").trim()
    }

    private fun parseChapterDate(dateStr: String): Long {
        val fullYear = dateFormatFull.tryParse(dateStr)
        if (fullYear != 0L) {
            return fullYear
        }
        return dateFormatShort.tryParse(dateStr)
    }

    // ============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val document = Jsoup.parse(html, response.request.url.toString())

        if (document.selectFirst("form.post-password-form input[name=post_password], input[name=post_password]") != null) {
            throw Exception(PASSWORD_WEBVIEW_MESSAGE)
        }

        val imageUrls = ImageDecryptor.extractImageUrls(html)

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index = index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = getFilters()

    companion object {
        private const val PASSWORD_WEBVIEW_MESSAGE = "Vui lòng nhập mật khẩu của chương này qua webview"

        private val dateFormatFull = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }

        private val dateFormatShort = SimpleDateFormat("dd/MM/yy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }

        private val CHAPTER_NAME_REGEX = Regex("chap\\s*\\d+(?:\\.\\d+)?", RegexOption.IGNORE_CASE)
        private val CHAPTER_WORD_REGEX = Regex("chap", RegexOption.IGNORE_CASE)
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val SMALL_THUMBNAIL_REGEX = Regex("-150x150(\\.[a-zA-Z]+)$")
    }
}
