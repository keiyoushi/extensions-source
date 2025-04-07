package eu.kanade.tachiyomi.extension.vi.hangtruyen

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HangTruyen :
    WPComics(
        "HangTruyen",
        "https://hangtruyen.net",
        "vi",
        dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        },
        gmtOffset = null,
    ),
    ConfigurableSource {

    // Popular
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/tim-kiem?r=newly-updated&page=$page&orderBy=view_desc")

    override fun popularMangaSelector() = "div.search-result div.row"

    override fun popularMangaNextPageSelector() = ".next-page"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select("div.search-result .m-post").map(::popularMangaFromElement)
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) } != null
        return MangasPage(entries, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val a = element.selectFirst("a")!!
        setUrlWithoutDomain(a.attr("abs:href"))
        title = a.attr("title")
        thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/tim-kiem?r=newly-updated&page=$page")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    // Search
    override val searchPath = "tim-kiem"

    override fun searchMangaSelector() = "div.search-result"

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.title-detail a")!!.text().trim()
        author = document.selectFirst("div.author p")?.text()?.trim()
        description = document.selectFirst("div.sort-des div.line-clamp")?.text()?.trim()
        genre = document.select("div.kind a, div.m-tags a").joinToString(", ") { it.text().trim() }
        status = when (document.selectFirst("div.status p")?.text()?.trim()) {
            "Đang tiến hành" -> SManga.ONGOING
            "Hoàn thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.col-image img")?.attr("abs:src")
    }

    // Chapters
    override fun chapterListSelector() = "div.list-chapters div.l-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.selectFirst("a.ll-chap")!!
        setUrlWithoutDomain(a.attr("href"))
        name = a.text().trim()
        date_upload = element.select("span.ll-update")[0].text().toDate()
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("#read-chaps .mi-item img.reading-img").mapIndexed { index, element ->
            val img = when {
                element.hasAttr("data-src") -> element.attr("abs:data-src")
                else -> element.attr("abs:src")
            }
            Page(index, imageUrl = img)
        }.distinctBy { it.imageUrl }
    }

    // Configurable domain
    private val preferences: SharedPreferences = getPreferences()

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
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(super.baseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: ${super.baseUrl}"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
