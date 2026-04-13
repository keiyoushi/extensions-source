package eu.kanade.tachiyomi.extension.vi.nettruyenviet

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class NetTruyenViet :
    HttpSource(),
    ConfigurableSource {

    override val name = "NetTruyenViet (unoriginal)"

    override val lang = "vi"

    private val defaultBaseUrl = "https://nettruyenviet10.com"

    override val baseUrl get() = getPrefBaseUrl()

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    private val preferences: SharedPreferences = getPreferences()

    private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/tim-truyen".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "10")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("div.items div.row > div.item").map(::mangaFromElement)
        return MangasPage(mangaList, hasNextPage(document))
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement: Element = element.selectFirst("figcaption h3 a.jtip, figcaption h3 a, .slide-caption h3 a")!!
        title = linkElement.text()
        setUrlWithoutDomain(linkElement.absUrl("href"))
        thumbnail_url = element.selectFirst("div.image img, img.image-thumb, a > img")
            ?.run {
                absUrl("data-original")
                    .ifEmpty { absUrl("data-src") }
                    .ifEmpty { absUrl("src") }
            }
            ?.takeUnless { it.isBlank() }
    }

    private fun hasNextPage(document: Document): Boolean = document.select("ul.pagination li.page-item:not(.disabled) > a")
        .any { link -> link.text() == NEXT_PAGE_SYMBOL }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val latestSection = document.selectFirst(
            "div.items:has(h1.page-title:matchesOwn(NetTruyen\\s*-\\s*Truyện tranh online))",
        )
        val mangaElements = latestSection?.select("div.row > div.item")
            ?: document.select("div.items div.row > div.item")

        val mangaList = mangaElements.map(::mangaFromElement)

        return MangasPage(mangaList, hasNextPage(document))
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-truyen".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        val websitePath = filters.firstInstanceOrNull<WebsiteFilter>()?.toUriPart()
        val sortValue = filters.firstInstanceOrNull<SortFilter>()?.toUriPart()
        val statusValue = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()

        val path = websitePath ?: genrePath ?: "/tim-truyen"
        val url = "$baseUrl$path".toHttpUrl().newBuilder().apply {
            if (path.startsWith("/tim-truyen")) {
                sortValue?.let { addQueryParameter("sort", it) }
                statusValue?.let { addQueryParameter("status", it) }
            }
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val infoElement = response.asJsoup().selectFirst("article#item-detail")!!

        return SManga.create().apply {
            title = infoElement.selectFirst("h1.title-detail, h1")!!.text()
            author = infoElement.selectFirst("li.author p.col-xs-8")?.text()
            status = infoElement.selectFirst("li.status p.col-xs-8")?.text().toStatus()
            genre = infoElement.select("li.kind p.col-xs-8 a")
                .joinToString { it.text() }
                .takeIf { it.isNotEmpty() }
                .takeUnless { it.equals("Đang cập nhật", ignoreCase = true) }
            thumbnail_url = infoElement.selectFirst("div.col-image img")
                ?.run {
                    absUrl("src")
                        .ifEmpty { absUrl("data-original") }
                        .ifEmpty { absUrl("data-src") }
                }
                ?.takeUnless { it.isBlank() }
            description = infoElement.selectFirst("div.detail-content div.shortened, div.detail-content")
                ?.clone()
                ?.apply {
                    select("h2.list-title, a.morelink, script, style").remove()
                }
                ?.text()
                ?.takeUnless { it.isBlank() }
        }
    }

    private fun String?.toStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        contains("Đang cập nhật", ignoreCase = true) -> SManga.ONGOING
        contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/truyen-tranh/").substringBefore("/")
            .ifEmpty { manga.url.substringAfterLast("/") }

        val url = "$baseUrl/Comic/Services/ComicService.asmx/ChapterList".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .build()

        val chapterHeaders = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return GET(url, chapterHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.queryParameter("slug") ?: return emptyList()
        val chapterItems = response.parseAs<ChapterListDto>().toChapterItems(slug)
        return chapterItems.map { chapterItem ->
            SChapter.create().apply {
                name = chapterItem.name
                setUrlWithoutDomain(chapterItem.url)
                date_upload = parseChapterDate(chapterItem.updatedAt)
                chapter_number = chapterItem.chapterNumber
            }
        }
    }

    private fun parseChapterDate(rawDate: String): Long = chapterDateFormat.tryParse(rawDate).takeIf { it > 0L } ?: parseRelativeDate(rawDate)

    private fun parseRelativeDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        val number = RELATIVE_DATE_NUMBER_REGEX.find(dateText)?.value?.toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))

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
        val imageUrls = response.asJsoup()
            .select("#chapter-content img, .reading-detail .page-chapter img, .chapter-content img, .page-chapter img")
            .map { imageElement ->
                imageElement.absUrl("data-src")
                    .ifEmpty { imageElement.absUrl("data-original") }
                    .ifEmpty { imageElement.absUrl("src") }
            }
            .filterNot { it.isEmpty() || NETTRUYEN_LOGO_URL_REGEX.containsMatchIn(it) }

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ===============================

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

    // ============================== Filters ================================

    override fun getFilterList(): FilterList = getFilters(
        genres = GenreFilterOptions,
        websites = WebsiteFilterOptions,
    )

    companion object {
        private val RELATIVE_DATE_NUMBER_REGEX = Regex("\\d+")
        private val NETTRUYEN_LOGO_URL_REGEX = Regex("/assets/images/nettruyenviet\\.webp", RegexOption.IGNORE_CASE)
        private const val NEXT_PAGE_SYMBOL = "›"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY = "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
