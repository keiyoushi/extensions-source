package eu.kanade.tachiyomi.extension.vi.muntruyen

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MunTruyen :
    HttpSource(),
    ConfigurableSource {

    override val name = "MunTruyen"

    override val lang = "vi"

    private val defaultBaseUrl = "https://moonnovel.store"

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

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .apply {
            build()["user-agent"]?.let { userAgent ->
                set("user-agent", userAgent.replace(WEBVIEW_TOKEN_REGEX, ")"))
            }
        }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = buildFilterUrl(page, sort = "views")
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseFilterPage(document)
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = buildFilterUrl(page, sort = "updated")
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseFilterPage(document)
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val pagePath = if (page > 1) "page/$page/" else ""
            val url = "$baseUrl/$pagePath".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
            return GET(url, headers)
        }

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val ageRatingFilter = filters.firstInstanceOrNull<AgeRatingFilter>()
        val authorFilter = filters.firstInstanceOrNull<AuthorFilter>()
        val teamFilter = filters.firstInstanceOrNull<TeamFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        val url = buildFilterUrl(
            page = page,
            status = statusFilter?.toUriPart().orEmpty(),
            ageRating = ageRatingFilter?.toUriPart().orEmpty(),
            author = authorFilter?.toUriPart().orEmpty(),
            team = teamFilter?.toUriPart().orEmpty(),
            sort = sortFilter?.toUriPart() ?: "updated",
            genres = genreFilter?.selectedValues().orEmpty(),
        )
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val requestUrl = response.request.url.toString()

        if (requestUrl.contains("?s=") || requestUrl.contains("&s=")) {
            return parseSearchPage(document)
        }

        return parseFilterPage(document)
    }

    private fun parseSearchPage(document: Document): MangasPage {
        val mangaList = document.select("article:has(a[href*=/truyen/]):not(:has(a[href*=/truyen/truyen-chu]))").map { article ->
            SManga.create().apply {
                val link = article.selectFirst("a[href*=/truyen/]")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = article.selectFirst("h2")!!.text()
                thumbnail_url = article.selectFirst("img")?.absUrl("src")
                    ?.replace(THUMBNAIL_SIZE_REGEX, ".")
            }
        }

        val hasNextPage = document.selectFirst("a[aria-label=Trang sau]") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun parseFilterPage(document: Document): MangasPage {
        val mangaList = document.select("h2 a[href*=/truyen/]").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = link.closest("div")
                    ?.parent()
                    ?.selectFirst("img[alt*=Ảnh bìa]")
                    ?.absUrl("src")
                    ?.replace(THUMBNAIL_SIZE_REGEX, ".")
            }
        }

        val hasNextPage = document.selectFirst("a[aria-label=Trang sau]") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun buildFilterUrl(
        page: Int,
        status: String = "",
        ageRating: String = "",
        author: String = "",
        team: String = "",
        sort: String = "updated",
        genres: List<String> = emptyList(),
    ): String {
        val pagePath = if (page > 1) "page/$page/" else ""
        val url = "$baseUrl/bo-loc-nang-cao/$pagePath".toHttpUrl().newBuilder()
            .addQueryParameter("type", "comic")
            .addQueryParameter("status", status)
            .addQueryParameter("age_rating", ageRating)
            .addQueryParameter("author", author)
            .addQueryParameter("team", team)
            .addQueryParameter("rating_min", "0")
            .addQueryParameter("rating_max", "6")
            .addQueryParameter("sort", sort)
        genres.forEach { url.addQueryParameter("genre[]", it) }
        return url.build().toString()
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1#manga-title")!!.text()
            status = parseStatus(document.selectFirst("#manga-status")?.text())
            genre = document.select("#genre-tags a[href*=/the-loai/]")
                .joinToString { it.text() }
            description = document.selectFirst("#manga-description")?.text()
            thumbnail_url = document.selectFirst(".story-cover-wrap a.story-cover img")?.absUrl("src")
        }
    }

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang tiến hành") -> SManga.ONGOING
        status.contains("Trọn bộ") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url.toString().removeSuffix("/")
        val chapters = mutableListOf<SChapter>()
        var page = 1

        while (true) {
            val doc = client.newCall(
                GET("$mangaUrl/chap/page/$page/", headers),
            ).execute().asJsoup()

            val pageChapters = parseChapterPage(doc)
            if (pageChapters.isEmpty()) break
            chapters.addAll(pageChapters)

            val hasNext = doc.select("nav[aria-label=Pagination] a[href*=/chap/page/]").any {
                it.text().toIntOrNull()?.let { num -> num > page } == true
            }
            if (!hasNext) break
            page++
        }

        return chapters
    }

    private fun parseChapterPage(document: Document): List<SChapter> = document.select(".chapter-item").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst("a[href*=/truyen/]")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = parseChapterName(element.selectFirst("h3")!!.text())
            date_upload = parseDate(element.selectFirst("time[datetime]")?.attr("datetime"))
        }
    }

    private fun parseChapterName(rawName: String): String {
        val index = rawName.indexOf("Chap", ignoreCase = true)
        return if (index >= 0) rawName.substring(index) else rawName
    }

    private fun parseDate(dateStr: String?): Long = DATE_FORMAT.tryParse(dateStr)

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#chapter-content img").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ==============================

    override fun getFilterList(): FilterList = getFilters()

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

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY = "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+\.""")
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
