package eu.kanade.tachiyomi.extension.vi.truyen18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class Truyen18 : HttpSource() {
    override val name = "Truyen18"

    override val lang = "vi"

    override val baseUrl = "https://truyen18.co"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(buildPagedUrl("/xem-nhieu-nhat", page), headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET(buildPagedUrl("/moi-cap-nhat", page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val searchPath = if (page > 1) "/search/page/$page" else "/search"
            val url = "$baseUrl$searchPath".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
            return GET(url, headers)
        }

        val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
            ?: return popularMangaRequest(page)

        return GET(buildPagedUrl("/category/$genreSlug", page), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("main h1")!!.text()
            thumbnail_url = document.selectFirst("main img[alt][src]")?.extractImageUrl()
            author = findInfoValue(document, "Tác giả")
            status = parseStatus(findInfoValue(document, "Trạng thái"))
            genre = parseGenres(document)
            description = document.selectFirst("main p.max-h-96")?.text()
        }
    }

    private fun parseGenres(document: Document): String? {
        val genres = document.select("main a[href*='/category/'], main a[href*='/tag/']")
            .map(Element::text)
            .distinct()

        if (genres.isEmpty()) return null

        return genres.joinToString()
    }

    private fun findInfoValue(document: Document, label: String): String? {
        val infoRow = document.select("main div.flex.items-center.space-x-2")
            .firstOrNull { row ->
                row.selectFirst("span.font-medium")?.text()?.equals("$label:", ignoreCase = true) == true
            }
            ?: return null

        return infoRow.children().getOrNull(1)?.text()
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("đang cập nhật", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("main section:has(h2:contains(Danh sách chương)) div.group.flex.items-center.justify-between")
            .mapNotNull { row ->
                val chapterLink = row.selectFirst("div.flex-1 a[href]:has(span.truncate)")
                    ?: row.selectFirst("div.flex-1 a[href]")
                    ?: return@mapNotNull null

                SChapter.create().apply {
                    name = chapterLink.text()
                    setUrlWithoutDomain(chapterLink.absUrl("href"))
                    date_upload = parseChapterDate(row.selectFirst("div.mt-1")?.text()?.substringAfter("Đăng lúc:"))
                }
            }
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        val relativeDate = parseRelativeDate(dateText)
        if (relativeDate != 0L) return relativeDate

        return chapterDateFormat.tryParse(dateText)
    }

    private fun parseRelativeDate(dateText: String): Long {
        val calendar = Calendar.getInstance(vietnamTimeZone)

        if (dateText.contains("vừa xong", ignoreCase = true)) {
            return calendar.timeInMillis
        }

        val number = DATE_NUMBER_REGEX.find(dateText)?.value?.toIntOrNull() ?: return 0L

        when {
            dateText.contains("giây", ignoreCase = true) -> calendar.add(Calendar.SECOND, -number)
            dateText.contains("phút", ignoreCase = true) -> calendar.add(Calendar.MINUTE, -number)
            dateText.contains("giờ", ignoreCase = true) -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateText.contains("ngày", ignoreCase = true) -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateText.contains("tuần", ignoreCase = true) -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateText.contains("tháng", ignoreCase = true) -> calendar.add(Calendar.MONTH, -number)
            dateText.contains("năm", ignoreCase = true) -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val chapterSlug = response.request.url.pathSegments.lastOrNull()
            ?: throw Exception("Không tìm thấy chương hiện tại")

        val chapterToken = findCurrentChapterToken(body, chapterSlug)
            ?: throw Exception("Không tìm thấy dữ liệu chương")

        val chapterContent = findChapterEscapedContent(body, chapterToken)
            ?: throw Exception("Không tìm thấy nội dung ảnh")

        val decodedContent = decodeEscapedContent(chapterContent)

        val imageUrls = IMAGE_SRC_REGEX.findAll(decodedContent)
            .map { it.groupValues[1] }
            .toList()
            .ifEmpty {
                ESCAPED_IMAGE_SRC_REGEX.findAll(body)
                    .map { it.groupValues[1] }
                    .filter { it.contains(chapterSlug) }
                    .toList()
            }

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun findCurrentChapterToken(body: String, chapterSlug: String): String? {
        val chapterSlugRegex = Regex.escape(chapterSlug)
        val tokenRegex = Regex(
            CURRENT_CHAPTER_TOKEN_REGEX.format(chapterSlugRegex),
            RegexOption.DOT_MATCHES_ALL,
        )

        return tokenRegex.find(body)?.groupValues?.get(1)
    }

    private fun findChapterEscapedContent(body: String, token: String): String? {
        val tokenContentRegex = Regex(
            TOKEN_CONTENT_REGEX.format(Regex.escape(token)),
            RegexOption.DOT_MATCHES_ALL,
        )

        val tokenContent = tokenContentRegex.find(body)?.groupValues?.get(1)
        if (tokenContent != null) return tokenContent

        return FIRST_CHAPTER_CONTENT_REGEX.find(body)?.groupValues?.get(1)
    }

    private fun decodeEscapedContent(content: String): String = content
        .replace("\\u003c", "<")
        .replace("\\u003e", ">")
        .replace("\\u0026", "&")
        .replace("\\u002F", "/")
        .replace("\\\"", "\"")
        .replace("\\/", "/")
        .replace("\\n", "\n")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("main div.grid a[href^=/doc-truyen]:has(h3)")
            .map { mangaElement ->
                SManga.create().apply {
                    title = mangaElement.selectFirst("h3")!!.text()
                    setUrlWithoutDomain(mangaElement.absUrl("href"))
                    thumbnail_url = mangaElement.closest("div.bg-white")
                        ?.selectFirst("div.relative img[src]")
                        ?.extractImageUrl()
                }
            }

        val hasNextPage = document.selectFirst("link[rel=next]") != null

        return MangasPage(mangaList, hasNextPage)
    }

    private fun Element.extractImageUrl(): String? {
        val rawUrl = absUrl("src")
        if (rawUrl.isBlank()) return null

        val imageParam = rawUrl.toHttpUrlOrNull()?.queryParameter("url")
            ?: return rawUrl

        return if (imageParam.startsWith("http")) {
            imageParam
        } else {
            "$baseUrl${if (imageParam.startsWith('/')) imageParam else "/$imageParam"}"
        }
    }

    private fun buildPagedUrl(path: String, page: Int): String = if (page > 1) {
        "$baseUrl$path/page/$page"
    } else {
        "$baseUrl$path"
    }

    companion object {
        private const val CURRENT_CHAPTER_TOKEN_REGEX =
            "\\\\\"slug\\\\\":\\\\\"%s\\\\\",\\\\\"content\\\\\":\\\\\"\\$([0-9a-z]+)\\\\\""

        private const val TOKEN_CONTENT_REGEX =
            """%s:T[0-9a-z]+,"\]\)</script><script>self\.__next_f\.push\(\[1,"(.*?)"\]\)</script>"""

        private val FIRST_CHAPTER_CONTENT_REGEX = Regex(
            """self\.__next_f\.push\(\[1,"(\\u003cp\\u003e\\u003cimg src=.*?)"\]\)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )

        private val vietnamTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

        private val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = vietnamTimeZone
        }

        private val DATE_NUMBER_REGEX = Regex("\\d+")
        private val IMAGE_SRC_REGEX = Regex("""src="(https?://[^"]+)"""")
        private val ESCAPED_IMAGE_SRC_REGEX = Regex("""src=\\"(https?://[^\\"]+)\\"""")
    }
}
