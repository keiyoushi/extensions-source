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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class LxHentai :
    HttpSource(),
    ConfigurableSource {

    override val name = "LXManga"

    override val lang = "vi"

    private val defaultBaseUrl = "https://lxmanga.space"

    override val baseUrl get() = getPrefBaseUrl()

    override val supportsLatest = true

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

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "-views")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("filter[status]", "ongoing,completed,paused")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "-updated_at")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("filter[status]", "ongoing,completed,paused")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ================================

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

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val mangaList = document.select("div.manga-vertical")
            .map(::mangaFromElement)
        val hasNextPage = document.select("a#pagination[data-page]")
            .mapNotNull { element -> element.attr("data-page").toIntOrNull() }
            .any { page -> page > currentPage }

        return MangasPage(mangaList, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga {
        val titleElement = element.selectFirst("a.text-ellipsis[href^=/truyen/]")!!
        val coverElement = element.selectFirst("div.cover")

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(titleElement.absUrl("href").toRelativeUrl())
            thumbnail_url = coverElement?.absUrl("data-bg")
                ?.ifEmpty { parseBackgroundUrl(coverElement.attr("style")).orEmpty() }
                ?.ifEmpty { null }
        }
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("div.flex.flex-row.truncate.mb-4 span.grow.text-lg.ml-1.text-ellipsis.font-semibold")!!.text()
            thumbnail_url = document.selectFirst("div.md\\:col-span-2 div.cover-frame > div.cover")
                ?.let { cover: Element ->
                    cover.absUrl("data-bg")
                        .ifEmpty { parseBackgroundUrl(cover.attr("style")).orEmpty() }
                        .ifEmpty { null }
                }
            author = document.infoRow("Tác giả:")
                ?.select("a[href*=/tac-gia/]")
                ?.joinToString { element -> element.text() }
                ?.ifEmpty { null }
            genre = document.infoRow("Thể loại:")
                ?.select("a[href*=/the-loai/]")
                ?.joinToString { element -> element.text() }
                ?.ifEmpty { null }
            description = document.select("div.pt-4.border-t")
                .firstOrNull { section ->
                    section.selectFirst("p.text-lg.font-semibold")?.text() == "Tóm tắt"
                }
                ?.selectFirst("p.whitespace-pre-wrap.break-words")
                ?.text()
                ?.ifEmpty { null }
            status = parseStatus(document.infoRow("Tình trạng:")?.text())
        }
    }

    private fun Document.infoRow(label: String): Element? = select("div.mt-2")
        .firstOrNull { row -> row.selectFirst("span.font-semibold")?.text() == label }

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

        return chapterElements.map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href").toRelativeUrl())
                name = element.selectFirst("span.text-ellipsis")!!.text()
                date_upload = parseChapterDate(element.selectFirst("span.timeago"))
            }
        }
    }

    private fun parseChapterDate(timeElement: Element?): Long = timeElement?.attr("datetime")
        ?.ifEmpty { null }
        ?.let { value -> DATE_TIME_FORMAT.tryParse(value).takeIf { parsed -> parsed != 0L } }
        ?: 0L

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val html = document.outerHtml()
        val actionToken = ACTION_TOKEN_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("Không tìm thấy action token")
        val encryptedPayload = ENCRYPTED_IMAGES_REGEX.find(html)?.groupValues?.get(1)
            ?: throw Exception("Không tìm thấy dữ liệu ảnh")

        val encryptedRows = ENCRYPTED_IMAGE_ROW_REGEX.findAll(encryptedPayload)
            .mapNotNull { row ->
                row.groupValues.getOrNull(1)
                    ?.split(',')
                    ?.mapNotNull(String::toIntOrNull)
                    ?.takeIf { values -> values.isNotEmpty() }
            }
            .toList()

        val imageUrls = document.select("#image-container[data-idx]")
            .mapNotNull { element -> element.attr("data-idx").toIntOrNull() }
            .distinct()
            .sorted()
            .mapNotNull { idx -> encryptedRows.getOrNull(idx) }
            .map { codes -> decodeImageUrl(codes, actionToken) }
            .filter(String::isNotBlank)
            .toList()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl ?: throw Exception("Không tìm thấy URL ảnh")
        return GET(imageUrl, imageHeaders())
    }

    private fun decodeImageUrl(codes: List<Int>, actionToken: String): String {
        val result = StringBuilder(codes.size)
        codes.forEachIndexed { index, code ->
            val keyCode = actionToken[index % actionToken.length].code
            result.append((code xor keyCode).toChar())
        }
        return result.toString()
    }

    private fun imageHeaders() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Token", "364b9dccc5ef526587f108c4d4fd63ee35286e19e36ec55b93bd4d79410dbbf6")
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

    private fun String.toRelativeUrl(): String {
        val httpUrl = toHttpUrlOrNull() ?: return this
        val query = httpUrl.encodedQuery?.let { value -> "?$value" }.orEmpty()
        return httpUrl.encodedPath + query
    }

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY = "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."

        private val BACKGROUND_URL_REGEX = Regex("""background-image:\s*url\(['"]?([^'")]+)""", RegexOption.IGNORE_CASE)
        private val ACTION_TOKEN_REGEX = Regex("""<meta\s+name=["']action_token["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val ENCRYPTED_IMAGES_REGEX = Regex("""var\s+_u\s*=\s*(\[\[.*?]]);""", setOf(RegexOption.DOT_MATCHES_ALL))
        private val ENCRYPTED_IMAGE_ROW_REGEX = Regex("""\[(\d+(?:,\d+)*)]""")

        private val DATE_TIME_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
