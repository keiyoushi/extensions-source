package eu.kanade.tachiyomi.extension.vi.lxhentai

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LxHentai :
    HttpSource(),
    ConfigurableSource {

    override val name = "LXManga"

    override val id = 6495630445796108150

    override val lang = "vi"

    override val supportsLatest = true

    private val defaultBaseUrl = "https://lxmanga.space"

    override val baseUrl get() = getPrefBaseUrl()

    private val preferences: SharedPreferences = getPreferences {
        getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = browseMangaRequest(page, "-views")

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = browseMangaRequest(page, "-updated_at")

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = when {
        query.startsWith(PREFIX_ID_SEARCH) -> {
            val slug = query.substringAfter(PREFIX_ID_SEARCH)
            val mangaUrl = "/truyen/$slug"
            fetchMangaDetails(SManga.create().apply { url = mangaUrl })
                .map {
                    it.url = mangaUrl
                    it.initialized = true
                    MangasPage(listOf(it), false)
                }
        }
        else -> super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "-updated_at"
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "name"
        val statuses = filters.firstInstanceOrNull<StatusFilter>()?.selectedValues().orEmpty()
        val includedGenres = filters.firstInstanceOrNull<GenreFilter>()?.includedValues().orEmpty()
        val excludedGenres = filters.firstInstanceOrNull<GenreFilter>()?.excludedValues().orEmpty()

        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("filter[$searchType]", query)
                }
                if (statuses.isNotEmpty()) {
                    addQueryParameter("filter[status]", statuses.joinToString(","))
                }
                if (includedGenres.isNotEmpty()) {
                    addQueryParameter("filter[accept_genres]", includedGenres.joinToString(","))
                }
                if (excludedGenres.isNotEmpty()) {
                    addQueryParameter("filter[reject_genres]", excludedGenres.joinToString(","))
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    override fun getFilterList(): FilterList = getFilters()

    private fun browseMangaRequest(page: Int, sortBy: String): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sortBy)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("filter[status]", "ongoing,completed,paused")
            .build()
        return GET(url, headers)
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val mangaList = document.select("div.manga-vertical")
            .map { element: Element -> mangaFromElement(element) }
        val hasNextPage = document.select("a#pagination[data-page]")
            .asSequence()
            .mapNotNull { element: Element -> element.attr("data-page").toIntOrNull() }
            .any { page: Int -> page > currentPage }

        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val titleElement = element.selectFirst("a.text-ellipsis[href^=/truyen/]")!!
        val coverElement = element.selectFirst("div.cover")

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(titleElement.absUrl("href"))
            thumbnail_url = coverElement?.let { it: Element -> getThumbnailUrl(it) }
        }
    }

    private fun getThumbnailUrl(element: Element): String? = element.absUrl("data-bg")
        .ifEmpty { parseBackgroundUrl(element.attr("style")).orEmpty() }
        .ifBlank { null }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("div.flex.flex-row.truncate.mb-4 span.grow.text-lg.ml-1.text-ellipsis.font-semibold")!!.text()
            thumbnail_url = document.selectFirst("div.md\\:col-span-2 div.cover-frame > div.cover")
                ?.let { element: Element -> getThumbnailUrl(element) }
            author = document.infoRow("Tác giả:")
                ?.select("a[href*=/tac-gia/]")
                ?.joinToString { it: Element -> it.text() }
                ?.ifEmpty { null }

            genre = document.infoRow("Thể loại:")
                ?.select("a[href*=/the-loai/]")
                ?.joinToString { it: Element -> it.text() }
                ?.ifEmpty { null }

            val altNames = document.infoRow("Tên khác:")
                ?.select("a, span:not(.font-semibold)")
                ?.joinToString { it.text().trim() }
                ?.takeIf { it.isNotBlank() }

            val summary = document.select("p:contains(Tóm tắt) ~ p").joinToString("\n") { it.wholeText() }.trim()

            description = buildString {
                if (altNames != null) {
                    append("Tên khác: ", altNames, "\n\n")
                }
                append(summary)
            }.trim()

            status = parseStatus(document.infoRow("Tình trạng:")?.text())
        }
    }

    private fun Document.infoRow(label: String): Element? = select("div")
        .firstOrNull { row: Element -> row.selectFirst("> span.font-semibold")?.text() == label }

    private fun parseStatus(rawStatus: String?): Int = when {
        rawStatus.isNullOrBlank() -> SManga.UNKNOWN
        rawStatus.contains("đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        rawStatus.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        rawStatus.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ===============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterElements = document.select("ul.overflow-y-auto a[href^=/truyen/]:has(span.timeago)")
            .ifEmpty { document.select("a[href^=/truyen/]:has(span.timeago)") }

        return chapterElements.map { element: Element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("span.text-ellipsis")?.text() ?: "Chapter"
                date_upload = parseChapterDate(element.selectFirst("span.timeago"))
            }
        }
    }

    private fun parseChapterDate(timeElement: Element?): Long = timeElement?.attr("datetime")
        ?.ifEmpty { null }
        ?.let { value: String -> DATE_TIME_FORMAT.tryParse(value).takeIf { parsed -> parsed != 0L } }
        ?: 0L

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val chapterUrl = response.request.url.toString()
        val document = response.asJsoup()

        // Try parse the encrypted payload + action_token
        val localImageUrls: List<String> = runCatching {
            val html = document.outerHtml()
            val actionToken = ACTION_TOKEN_REGEX.find(html)?.groupValues?.get(1) ?: return@runCatching emptyList()
            val encryptedPayload = ENCRYPTED_IMAGES_REGEX.find(html)?.groupValues?.get(1) ?: return@runCatching emptyList()
            val encryptedRows = ENCRYPTED_IMAGE_ROW_REGEX.findAll(encryptedPayload)
                .mapNotNull { row: MatchResult ->
                    row.groupValues.getOrNull(1)
                        ?.split(',')
                        ?.mapNotNull { it.toIntOrNull() }
                        ?.takeIf { it.isNotEmpty() }
                }
                .toList()
            document.select("#image-container[data-idx]")
                .asSequence()
                .mapNotNull { element: Element -> element.attr("data-idx").toIntOrNull() }
                .distinct()
                .sorted()
                .mapNotNull { idx: Int -> encryptedRows.getOrNull(idx) }
                .map { codes: List<Int> -> decodeImageUrl(codes, actionToken) }
                .filter { it.isNotBlank() }
                .toList()
        }.getOrDefault(emptyList())

        // Token that images require as the `Token` header must come from /get_token, which
        // is gated behind Cloudflare Turnstile. Run the chapter page in a headless WebView
        // so the site's own JS solves Turnstile, calls /get_token and exposes the result via
        // window.actionToken (and the decoded URLs via window.__imgSrcs).
        val webViewData = TokenResolver.resolve(chapterUrl)

        val imageUrls = webViewData.srcs.filter { it.isNotBlank() }
            .ifEmpty { localImageUrls }

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy dữ liệu ảnh")
        }

        val pageMetadata = encodePageMetadata(chapterUrl, webViewData.token)
        return imageUrls.mapIndexed { index: Int, imageUrl: String ->
            Page(index, pageMetadata, imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request {
        val (chapterUrl, actionToken) = decodePageMetadata(page.url)
        val imageUrl = page.imageUrl ?: throw Exception("Không tìm thấy URL ảnh")
        return GET(imageUrl, imageHeaders(chapterUrl, actionToken))
    }

    private fun decodeImageUrl(codes: List<Int>, actionToken: String): String {
        val result = StringBuilder(codes.size)
        codes.forEachIndexed { index, code ->
            val keyCode = actionToken[index % actionToken.length].code
            result.append((code xor keyCode).toChar())
        }
        return result.toString()
    }

    private fun imageHeaders(chapterUrl: String, actionToken: String) = super.headersBuilder()
        .add("Referer", chapterUrl)
        .add("Origin", baseUrl)
        .add("Token", actionToken)
        .build()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    // ============================== Helpers ===============================

    private fun parseBackgroundUrl(styleValue: String?): String? {
        if (styleValue.isNullOrBlank()) return null

        val rawUrl = BACKGROUND_URL_REGEX.find(styleValue)?.groupValues?.get(1) ?: return null
        return rawUrl.toHttpUrlOrNull()?.toString()
            ?: "$baseUrl${rawUrl.takeIf { it.startsWith("/") } ?: "/$rawUrl"}"
    }

    private fun encodePageMetadata(chapterUrl: String, actionToken: String): String = "$chapterUrl\n$actionToken"

    private fun decodePageMetadata(rawMetadata: String): Pair<String, String> {
        val separatorIndex = rawMetadata.lastIndexOf('\n')
        if (separatorIndex <= 0 || separatorIndex == rawMetadata.lastIndex) {
            throw Exception("Không đọc được thông tin token ảnh")
        }

        val chapterUrl = rawMetadata.substring(0, separatorIndex)
        val actionToken = rawMetadata.substring(separatorIndex + 1)
        return chapterUrl to actionToken
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY = "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."

        private val BACKGROUND_URL_REGEX = Regex("""background-image:\s*url\(['"]?([^'")]+)""", RegexOption.IGNORE_CASE)
        private val ACTION_TOKEN_REGEX = Regex("""<meta\s+name=["']action_token["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val ENCRYPTED_IMAGES_REGEX = Regex("""var\s+_u\s*=\s*(\[\[.*?]]);""", RegexOption.DOT_MATCHES_ALL)
        private val ENCRYPTED_IMAGE_ROW_REGEX = Regex("""\[(\d+(?:,\d+)*)]""")

        private val DATE_TIME_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
