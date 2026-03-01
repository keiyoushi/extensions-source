package eu.kanade.tachiyomi.extension.vi.newtruyentranh

import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class NewTruyenTranh :
    HttpSource(),
    ConfigurableSource {
    override val name = "NewTruyenTranh"
    override val lang = "vi"
    private val defaultBaseUrl = "https://newtruyentranh7.com"
    override val baseUrl by lazy { getPrefBaseUrl() }
    override val supportsLatest = true

    private val preferences = getPreferences()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 20, 1, TimeUnit.MINUTES)
        .addNetworkInterceptor {
            val request = it.request()
            val response = it.proceed(request)

            if (request.url.toString().startsWith(baseUrl)) {
                if (response.code == 429) {
                    throw IOException("Bạn đang request quá nhanh!")
                }
            }
            response
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val searchUrl get() = "$baseUrl/tim-truyen-nang-cao"

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = searchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("status", "-1")
            .addQueryParameter("sort", "10")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = searchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("status", "-1")
            .addQueryParameter("sort", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            } else {
                var sortValue = "0"
                var statusValue = "-1"

                filters.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> {
                            if (filter.state != 0) {
                                addQueryParameter("categories", filter.toUriPart())
                            }
                        }
                        is SortFilter -> {
                            if (filter.state != 0) {
                                sortValue = filter.toUriPart()
                            }
                        }
                        is StatusFilter -> {
                            statusValue = filter.toUriPart()
                        }
                        else -> {}
                    }
                }

                addQueryParameter("status", statusValue)
                addQueryParameter("sort", sortValue)
            }
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    private fun parseMangaListPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".items .item").map { element ->
            SManga.create().apply {
                val linkElement = element.selectFirst("h3 a")!!
                title = linkElement.text()
                setUrlWithoutDomain(linkElement.absUrl("href"))
                val imgElement = element.selectFirst(".image img")
                thumbnail_url = imgElement?.absUrl("data-original")
                    .takeIf { !it.isNullOrEmpty() }
                    ?: imgElement?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".pagination li:last-child a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        SortFilter(),
        StatusFilter(),
    )

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.title-detail")!!.text()
            thumbnail_url = document.selectFirst(".col-image img")?.absUrl("src")
            author = document.selectFirst(".author .col-xs-10")?.text()
            status = document.selectFirst(".status .col-xs-10")?.text()
                ?.let { parseStatus(it) }
                ?: SManga.UNKNOWN
            genre = document.select(".kind a")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst("#summary")?.text()
        }
    }

    private fun parseStatus(status: String): Int {
        val ongoingWords = listOf("Đang Cập Nhật", "Đang Tiến Hành")
        val completedWords = listOf("Hoàn Thành", "Đã Hoàn Thành")
        val hiatusWords = listOf("Tạm ngưng", "Tạm hoãn")
        return when {
            ongoingWords.any { status.contains(it, ignoreCase = true) } -> SManga.ONGOING
            completedWords.any { status.contains(it, ignoreCase = true) } -> SManga.COMPLETED
            hiatusWords.any { status.contains(it, ignoreCase = true) } -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun String?.parseDate(): Long {
        this ?: return 0L

        if (this.contains("trước", ignoreCase = true)) {
            return try {
                val calendar = Calendar.getInstance()

                val patterns = listOf(
                    Regex("""(\d+)\s*giờ""", RegexOption.IGNORE_CASE) to Calendar.HOUR_OF_DAY,
                    Regex("""(\d+)\s*ngày""", RegexOption.IGNORE_CASE) to Calendar.DAY_OF_MONTH,
                    Regex("""(\d+)\s*tuần""", RegexOption.IGNORE_CASE) to Calendar.WEEK_OF_YEAR,
                    Regex("""(\d+)\s*tháng""", RegexOption.IGNORE_CASE) to Calendar.MONTH,
                    Regex("""(\d+)\s*năm""", RegexOption.IGNORE_CASE) to Calendar.YEAR,
                    Regex("""(\d+)\s*phút""", RegexOption.IGNORE_CASE) to Calendar.MINUTE,
                    Regex("""(\d+)\s*giây""", RegexOption.IGNORE_CASE) to Calendar.SECOND,
                )

                for ((pattern, field) in patterns) {
                    pattern.find(this)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
                        calendar.add(field, -number)
                        return calendar.timeInMillis
                    }
                }

                0L
            } catch (_: Exception) {
                0L
            }
        }

        return dateFormat.tryParse(this)
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request {
        val comicId = manga.url.substringAfterLast("-").trimEnd('/')
        return GET("$baseUrl/Story/ListChapterByStoryID?storyID=$comicId", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("li.row:not(.heading)").map { row ->
            SChapter.create().apply {
                val linkElement = row.selectFirst(".chapter a")!!
                setUrlWithoutDomain(linkElement.absUrl("href"))
                name = linkElement.text()
                date_upload = row.selectFirst(".col-xs-4")?.text()?.parseDate() ?: 0L
            }
        }
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".page-chapter img").mapIndexed { index, img ->
            val imageUrl = img.absUrl("data-original")
                .ifEmpty { img.absUrl("src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // =============================== Related ================================
    // dirty hack to disable suggested mangas on Komikku due to heavy rate limit
    // https://github.com/komikku-app/komikku/blob/4323fd5841b390213aa4c4af77e07ad42eb423fc/source-api/src/commonMain/kotlin/eu/kanade/tachiyomi/source/CatalogueSource.kt#L176-L184
    @Suppress("Unused")
    @JvmName("getDisableRelatedMangasBySearch")
    fun disableRelatedMangasBySearch() = true

    // ============================== Preferences ===========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."

        private val dateFormat by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
        }
    }
}
