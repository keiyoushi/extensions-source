package eu.kanade.tachiyomi.extension.vi.doctruyen3q

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DocTruyen3Q :
    WPComics(
        "DocTruyen3Q",
        "https://doctruyen3qui19.com",
        "vi",
        dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        },
        gmtOffset = null,
    ),
    ConfigurableSource {

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".page-chapter[id] img").mapIndexed { index, element ->
            val img = element.attr("abs:src").let { url ->
                if (url.startsWith("//")) {
                    "https:$url"
                } else {
                    url
                }
            }
            Page(index, imageUrl = img)
        }.distinctBy { it.imageUrl }
    }

    override fun popularMangaSelector() = "div.item-manga div.item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("h3 a")?.let {
            title = it.text()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        thumbnail_url = imageOrNull(element.selectFirst("img")!!)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart()?.let { url.addPathSegment(it) }
                is StatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                else -> {}
            }
        }

        when {
            query.isNotBlank() -> url.addQueryParameter(queryParam, query)
            else -> url.addQueryParameter("page", page.toString())
        }

        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.title-manga")!!.text()
        description = document.select("p.detail-summary").joinToString { it.wholeText().trim() }
        status = document.selectFirst("li.status p.detail-info span")?.text().toStatus()
        genre = document.select("li.category p.detail-info a").joinToString { it.text() }
        thumbnail_url = imageOrNull(document.selectFirst("img.image-comic")!!)
    }

    override fun chapterListSelector() = "div.list-chapter li.row:not(.heading):not([style])"

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            date_upload = element.selectFirst(".chapters + div")?.text().toDate()
        }
    }

    override val genresSelector = ".categories-detail ul.nav li:not(.active) a"

    // Configurable, automatic change domain
    private val preferences: SharedPreferences = getPreferences()
    private var hasCheckedRedirect = false

    // Catch redirects
    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val response = chain.proceed(originalRequest)
            if (!hasCheckedRedirect && preferences.getBoolean(AUTO_CHANGE_DOMAIN_PREF, false)) {
                hasCheckedRedirect = true
                val originalHost = super.baseUrl.toHttpUrl().host
                val newHost = response.request.url.host
                if (newHost != originalHost) {
                    val newBaseUrl = "${response.request.url.scheme}://$newHost"
                    preferences.edit()
                        .putString(BASE_URL_PREF, newBaseUrl)
                        .apply()
                }
            }
            response
        }
        .rateLimit(5)
        .build()

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, super.baseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                    .apply()
            }
        }
    }

    override val baseUrl by lazy { getPrefBaseUrl() }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val defaultUrl = super.baseUrl
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(defaultUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultUrl"
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(baseUrlPref)

        val autoDomainPref = androidx.preference.SwitchPreferenceCompat(screen.context).apply {
            key = AUTO_CHANGE_DOMAIN_PREF
            title = AUTO_CHANGE_DOMAIN_TITLE
            summary = AUTO_CHANGE_DOMAIN_SUMMARY
            setDefaultValue(false)
        }
        screen.addPreference(autoDomainPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
        private const val AUTO_CHANGE_DOMAIN_PREF = "autoChangeDomain"
        private const val AUTO_CHANGE_DOMAIN_TITLE = "Tự động cập nhật domain"
        private const val AUTO_CHANGE_DOMAIN_SUMMARY =
            "Khi mở ứng dụng, ứng dụng sẽ tự động cập nhật domain mới nếu website chuyển hướng."
    }
}
