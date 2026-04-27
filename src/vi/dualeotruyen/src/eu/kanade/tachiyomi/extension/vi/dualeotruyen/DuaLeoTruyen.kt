package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import android.content.SharedPreferences
import android.util.Base64
import android.widget.Toast
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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DuaLeoTruyen :
    HttpSource(),
    ConfigurableSource {
    override val name = "Dưa Leo Truyện"
    override val lang = "vi"
    override val supportsLatest = true

    private val defaultBaseUrl = "https://dualeotruyendb.com"
    private val preferences: SharedPreferences = getPreferences()

    override val baseUrl get() = getPrefBaseUrl()

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

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/truyen-tranh-hot?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/truyen-moi-cap-nhat?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .build()
            return GET(url, headers)
        }

        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()

        if (genrePath != null) {
            return GET("$baseUrl$genrePath?page=$page", headers)
        }

        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaListParse(response)

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== List ==================================

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".box_list .li_truyen").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".pagination a.next") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a[href*=/truyen-tranh/]")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = linkElement.selectFirst(".name")!!.text()
        thumbnail_url = linkElement.selectFirst(".img img")?.absUrl("data-src")
            ?: linkElement.selectFirst(".img img")?.absUrl("src")
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".box_info_right h1")!!.text()
            genre = document.select(".list-tag-story a")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst(".story-detail-info")
                ?.text()
                ?.ifEmpty { null }
            status = parseStatus(
                document.select(".info-item")
                    .firstOrNull { it.text().contains("Tình trang") }
                    ?.text(),
            )
            thumbnail_url = document.selectFirst(".box_info_left .img img")?.absUrl("src")
        }
    }

    private fun parseStatus(statusText: String?): Int {
        val normalized = statusText?.lowercase(Locale.ROOT)

        return when {
            normalized == null -> SManga.UNKNOWN
            "hoàn thành" in normalized -> SManga.COMPLETED
            "đang cập nhật" in normalized -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup()
        .select(".chapter-item")
        .map { element ->
            SChapter.create().apply {
                val linkElement = element.selectFirst(".chap_name a")!!
                setUrlWithoutDomain(linkElement.absUrl("href"))
                name = linkElement.text()
                date_upload = parseDate(element.selectFirst(".chap_update")?.text())
            }
        }

    private fun parseDate(dateStr: String?): Long = DATE_FORMAT.tryParse(dateStr)

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".content_view_chap img")
            .mapNotNull { img ->
                val dataImg = img.attr("data-img")
                val src = img.absUrl("src")

                when {
                    dataImg.isNotBlank() -> decryptImageUrl(dataImg)
                    src.isNotBlank() && !src.startsWith("data:") -> src
                    else -> null
                }
            }
            .distinct()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    private fun decryptImageUrl(url: String): String? {
        val lastSlashIndex = url.lastIndexOf('/')
        val dotIndex = url.lastIndexOf('.')
        if (lastSlashIndex == -1 || dotIndex == -1 || dotIndex <= lastSlashIndex) return null

        val basePath = url.substring(0, lastSlashIndex + 1)
        val encodedName = url.substring(lastSlashIndex + 1, dotIndex)
        val extension = url.substring(dotIndex)

        val base64 = encodedName.replace('-', '+').replace('_', '/')
        val decoded = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: Exception) {
            return url
        }

        val decrypted = ByteArray(decoded.size) { i ->
            (decoded[i].toInt() xor DECRYPT_SALT[i % DECRYPT_SALT.length].code).toByte()
        }

        val decryptedName = String(decrypted, Charsets.UTF_8)
        return "$basePath$decryptedName$extension"
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Preferences ===========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
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
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."

        private const val DECRYPT_SALT = "dualeo_salt_2025"

        private val DATE_FORMAT = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
