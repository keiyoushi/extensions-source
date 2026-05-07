package eu.kanade.tachiyomi.extension.vi.moetruyen

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class MoeTruyen :
    HttpSource(),
    ConfigurableSource {
    override val name = "MoeTruyen"
    override val lang = "vi"
    override val baseUrl get() = getPrefBaseUrl()
    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup()
            .select("ol.homepage-ranking-list[data-ranking-period=total] a.homepage-ranking-item__link")
            .map(::popularMangaFromElement)

        return MangasPage(mangas, false)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val titleElement = element.selectFirst(".homepage-ranking-item__title")!!
        val titleAttr = titleElement.attr("title")
        title = titleAttr.ifEmpty { titleElement.text() }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    private fun latestMangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a[href^=/manga/]")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = getFullListTitle(element)
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private fun getFullListTitle(element: Element): String {
        val titleElement = element.selectFirst("h3")!!
        val titleAttr = titleElement.attr("title")
        if (titleAttr.isNotEmpty()) {
            return titleAttr
        }

        val titleText = titleElement.text()
        if (!titleText.endsWith("...")) {
            return titleText
        }

        val imageAlt = element.selectFirst("img")?.attr("alt")
            ?.removePrefix("Bìa ")
            ?.trim()
            ?.ifEmpty { null }

        return imageAlt ?: titleText
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("article.manga-card--list")
            .map(::latestMangaFromElement)

        val hasNextPage = document
            .selectFirst("nav[aria-label='Phân trang truyện'] a[aria-label='Trang sau']:not(.is-disabled)")
            ?.attr("href")
            ?.let { it != "#" }
            ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
        val includedGenres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            .orEmpty()
        val hasFilter = status != null || includedGenres.isNotEmpty()

        if (query.isBlank() && !hasFilter) {
            return latestUpdatesRequest(page)
        }

        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("q", query)
                }

                status?.let { addQueryParameter("status", it) }
                includedGenres.forEach { addQueryParameter("include", it.id) }
            }
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.manga-detail-title")!!.text()
            author = document.select("p.manga-detail-meta-line")
                .firstOrNull { line ->
                    line.selectFirst(".manga-detail-meta-label")
                        ?.text()
                        ?.contains("Tác giả")
                        ?: false
                }
                ?.select("a.inline-link")
                ?.joinToString { it.text() }
                ?.ifEmpty { null }
            genre = document.select(".manga-detail-genre-chips a.chip")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst("[data-description-content]")
                ?.text()
                ?.ifEmpty { null }
                ?: document.selectFirst(".manga-description__text")
                    ?.text()
                    ?.ifEmpty { null }
            status = parseStatus(document.selectFirst(".manga-status-pill")?.text())
            thumbnail_url = document.selectFirst(".detail-cover img")?.absUrl("src")
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()) {
        "Còn tiếp" -> SManga.ONGOING
        "Hoàn thành" -> SManga.COMPLETED
        "Tạm dừng" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): rx.Observable<List<SChapter>> = rx.Observable.fromCallable {
        client.newCall(chapterListRequest(manga)).execute().use { response ->
            chapterListParsePaginated(response)
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> = parseChapterList(response.asJsoup())

    private fun chapterListParsePaginated(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val visitedPages = mutableSetOf<String>()
        var currentPageUrl = response.request.url.toString()
        var currentDocument = response.asJsoup()

        while (visitedPages.add(currentPageUrl)) {
            chapters += parseChapterList(currentDocument)

            val nextChapterLinkElement: Element? = currentDocument.selectFirst(
                "nav[aria-label*='Phân trang chương'] a[aria-label='Trang chương sau']:not(.is-disabled)",
            )
            val nextChapterPageUrl: String? = nextChapterLinkElement?.let { link ->
                if (link.attr("href") == "#") {
                    null
                } else {
                    link.absUrl("href").ifEmpty { null }
                }
            }

            if (nextChapterPageUrl == null || visitedPages.contains(nextChapterPageUrl)) {
                break
            }

            currentPageUrl = nextChapterPageUrl
            client.newCall(GET(currentPageUrl, headers)).execute().use {
                currentDocument = it.asJsoup()
            }
        }

        return chapters
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("ul.chapter-list li.chapter a.chapter-link").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst(".chapter-num")!!.text()

            val chapterTime = element.selectFirst(".chapter-time")
            val relativeDate = chapterTime?.text()
            val absoluteDate = chapterTime?.attr("title")
                ?.substringAfter("Cập nhật", missingDelimiterValue = "")
                ?.trim()
                ?.ifEmpty { null }

            date_upload = parseRelativeDate(relativeDate).takeIf { it != 0L }
                ?: dateFormat.tryParse(absoluteDate)
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance()
        val number = NUMBER_REGEX.find(dateStr)?.value?.toIntOrNull() ?: return 0L

        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document.select("img.page-media")
            .asSequence()
            .filterNot { element ->
                element.parents().any { parent -> parent.tagName().equals("noscript", ignoreCase = true) }
            }
            .map { element ->
                element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            }
            .filter { imageUrl ->
                imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
            }
            .distinct()
            .toList()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Preferences ===========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val customUrlPref = EditTextPreference(screen.context).apply {
            key = PREF_CUSTOM_DOMAIN
            title = "Tên miền tùy chỉnh"
            summary = "Nhập tên miền bạn muốn sử dụng (ví dụ: https://moetruyen.xyz)"
            setEnabled(preferences.getPrefUrl() == UrlMode.CUSTOM)
            dialogTitle = "Tên miền tùy chỉnh"
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val inputUrl = newValue as String
                    if (inputUrl.isNotBlank()) {
                        inputUrl.toHttpUrl()
                    }
                    Toast.makeText(screen.context, NOTIFICATION_SHOW, Toast.LENGTH_SHORT).show()
                    true
                } catch (e: Exception) {
                    Toast.makeText(screen.context, "Tên miền không hợp lệ: Error: ${e.message} ", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }

        ListPreference(screen.context).apply {
            key = PREF_DOMAIN
            title = "Tên miền chính"
            entries = LIST_DOMAIN_ENTRIES
            entryValues = LIST_DOMAIN_VALUES
            summary = "%s"
            setDefaultValue("default")
            setOnPreferenceChangeListener { _, newValue ->
                val index = entryValues.indexOf(newValue as String)
                summary = entries[index]
                customUrlPref.setEnabled(newValue == "custom")
                true
            }
        }.let(screen::addPreference)
        customUrlPref.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = when (preferences.getPrefUrl()) {
        UrlMode.DEFAULT -> DEFAULT_DOMAIN
        UrlMode.GLOBAL -> DOMAIN_GLOBAL
        UrlMode.CUSTOM -> preferences.getString(PREF_CUSTOM_DOMAIN, DEFAULT_DOMAIN)!!
    }.removeSuffix("/")
    enum class UrlMode {
        DEFAULT,
        GLOBAL,
        CUSTOM,
    }
    private fun SharedPreferences.getPrefUrl(): UrlMode = when (getString(PREF_DOMAIN, "default")) {
        "default" -> UrlMode.DEFAULT
        "global" -> UrlMode.GLOBAL
        else -> UrlMode.CUSTOM
    }
    companion object {
        private const val PREF_DOMAIN = "pref_domain"
        private const val DEFAULT_DOMAIN = "https://moetruyen.net"
        private const val DOMAIN_GLOBAL = "https://truyen.moe"
        private val LIST_DOMAIN_ENTRIES = arrayOf(
            "MoeTruyen.net (Trong nước)",
            "Truyen.moe (Quốc tế)",
            "Tùy chỉnh",
        )
        private val LIST_DOMAIN_VALUES = arrayOf(
            "default",
            "global",
            "custom",
        )
        private const val PREF_CUSTOM_DOMAIN = "pref_custom_domain"
        private const val NOTIFICATION_SHOW = "Tên miền đã được thay đổi."
        private val NUMBER_REGEX = Regex("""\d+""")
        private val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
