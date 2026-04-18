package eu.kanade.tachiyomi.extension.vi.meosua

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MeoSua : HttpSource() {
    override val name = "MeoSua"
    override val lang = "vi"
    override val baseUrl = "https://meosua.com"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    // Strip "wv" from User-Agent so Google login works in this source.
    // Google deny login when User-Agent contains the WebView token.
    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .apply {
            build()["user-agent"]?.let { userAgent ->
                set("user-agent", removeWebViewToken(userAgent))
            }
        }

    private fun removeWebViewToken(userAgent: String): String = userAgent.replace(WEBVIEW_TOKEN_REGEX, ")")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/xem-nhieu-nhat/".toHttpUrl().newBuilder()
            .addQueryParameter("trang", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/truyen-moi-cap-nhat/".toHttpUrl().newBuilder()
            .addQueryParameter("trang", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) {
            return latestUpdatesRequest(page)
        }

        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup()
            .select("article.uk-panel.uk-margin-small-bottom:has(h3 a[href*=\"/truyen/\"])")
            .map(::mangaFromElement)
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseMangaList(document: org.jsoup.nodes.Document): MangasPage {
        val mangas = document.select("article.uk-panel.uk-margin-small-bottom:has(h3 a[href*=\"/truyen/\"])")
            .map(::mangaFromElement)
            .distinctBy { it.url }
        val hasNextPage = document.selectFirst(".uk-pagination [uk-pagination-next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val titleLink = element.selectFirst("h3 a[href*=\"/truyen/\"]")!!
        val mangaUrl = titleLink.absUrl("href").substringBefore("?")

        return SManga.create().apply {
            title = titleLink.text()
            setUrlWithoutDomain(mangaUrl)
            thumbnail_url = element.selectFirst("img")?.let(::imageUrlFromElement)
        }
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(
                "h2#category-title, section#single-block h2, #single-block h2.uk-margin-remove-top, h2.uk-margin-remove-top",
            )!!.text()
            thumbnail_url = document.selectFirst(".single-thumb img")?.absUrl("src")
            status = document.selectFirst(".tab-story [uk-icon=\"icon: file-edit\"]")
                ?.parent()
                ?.text()
                ?.let(::parseStatus)
                ?: SManga.UNKNOWN
            genre = document.select(".tab-story a[href*=\"/the-loai/\"]")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst(".tab-story .hide-long-text p")
                ?.text()
                ?.ifEmpty {
                    document.selectFirst(".tab-story h3:contains(Tóm tắt) + p")
                        ?.text()
                }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val initialDocument = response.asJsoup()
        val allChapters = mutableListOf<SChapter>()

        allChapters += initialDocument
            .select("#chapter-list-tab .chapter-item, .tab-story .chapter-list .chapter-item")
            .mapNotNull(::chapterFromElement)

        val maxPage = initialDocument.select("#chapter-list-tab .uk-pagination a[href*=\"?trang=\"]")
            .mapNotNull {
                CHAPTER_PAGE_REGEX.find(it.absUrl("href"))
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
            .maxOrNull()
            ?: 1

        for (page in 2..maxPage) {
            val pageUrl = response.request.url.newBuilder()
                .setQueryParameter("trang", page.toString())
                .build()

            client.newCall(GET(pageUrl, headers)).execute().use { pageResponse ->
                val pageDocument = pageResponse.asJsoup()
                allChapters += pageDocument
                    .select("#chapter-list-tab .chapter-item, .tab-story .chapter-list .chapter-item")
                    .mapNotNull(::chapterFromElement)
            }
        }

        return allChapters.distinctBy { it.url }
    }

    private fun chapterFromElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.uk-link-toggle") ?: return null
        val chapterUrl = linkElement.absUrl("href")
        if (chapterUrl.isBlank()) return null

        val chapterNameRaw = linkElement.selectFirst("h4")?.text() ?: return null
        val chapterName = CHAPTER_NAME_REGEX.find(chapterNameRaw)
            ?.value
            ?.replace(CHAPTER_WORD_REGEX, "Chap")
            ?.replace(MULTI_SPACE_REGEX, " ")
            ?: chapterNameRaw

        val isLocked = element.selectFirst("[uk-icon=\"icon: lock\"], .uk-text-danger[uk-icon]") != null
        val chapterDate = element.selectFirst(".uk-article-meta [uk-icon=\"icon: calendar\"] + span")?.text()

        return SChapter.create().apply {
            setUrlWithoutDomain(chapterUrl)
            name = if (isLocked) "🔒 $chapterName" else chapterName
            date_upload = chapterDate?.let(DATE_FORMAT::tryParse) ?: 0L
        }
    }

    private fun parseStatus(statusText: String): Int = when {
        statusText.contains("Trọn bộ", ignoreCase = true) -> SManga.COMPLETED
        statusText.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.selectFirst("#view-chapter .lock-card, #view-chapter #unlock-chapter, #view-chapter #xu-lock") != null) {
            throw Exception(LOCKED_CHAPTER_MESSAGE)
        }

        val imageUrls = document.select("#view-chapter .chapter-content img")
            .ifEmpty { document.select(".chapter-content img, .view-comic img") }
            .mapNotNull(::imageUrlFromElement)
            .filterNot { PLACEHOLDER_IMAGE_REGEX.containsMatchIn(it) }

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun imageUrlFromElement(element: Element): String? = element.absUrl("data-src")
        .ifEmpty { element.absUrl("data-lazy-src") }
        .ifEmpty { element.absUrl("src") }
        .ifEmpty { null }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val LOCKED_CHAPTER_MESSAGE =
            "Vui lòng đăng nhập vào tài khoản phù hợp bằng webview để xem chương này"

        private val CHAPTER_NAME_REGEX = Regex("chap\\s*\\d+(?:\\.\\d+)?", RegexOption.IGNORE_CASE)
        private val CHAPTER_WORD_REGEX = Regex("chap", RegexOption.IGNORE_CASE)
        private val CHAPTER_PAGE_REGEX = Regex("[?&]trang=(\\d+)")
        private val MULTI_SPACE_REGEX = Regex("\\s+")
        private val PLACEHOLDER_IMAGE_REGEX = Regex("/wp-content/uploads/\\d{4}/\\d{2}/(?:0|999)\\.webp$", RegexOption.IGNORE_CASE)
        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
