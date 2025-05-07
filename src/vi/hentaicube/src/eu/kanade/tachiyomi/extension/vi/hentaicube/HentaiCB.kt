package eu.kanade.tachiyomi.extension.vi.hentaicube

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiCB :
    Madara(
        "CBHentai",
        "https://hentaicb.pics",
        "vi",
        SimpleDateFormat("dd/MM/yyyy", Locale("vi")),
    ),
    ConfigurableSource {

    override val id: Long = 823638192569572166

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

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
    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val filterNonMangaItems = false

    override val mangaSubString = "read"

    override val altNameSelector = ".post-content_item:contains(Tên khác) .summary-content"

    private val thumbnailOriginalUrlRegex = Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$")

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            element.selectFirst("img")?.let { img ->
                thumbnail_url = imageFromElement(img)?.replace(thumbnailOriginalUrlRegex, "$1")
            }
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val mangaUrl = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment(mangaSubString)
                addPathSegment(query.substringAfter(URL_SEARCH_PREFIX))
                addPathSegment("")
            }.build()
            return client.newCall(GET(mangaUrl, headers))
                .asObservableSuccess().map { response ->
                    val manga = mangaDetailsParse(response).apply {
                        setUrlWithoutDomain(mangaUrl.toString())
                        initialized = true
                    }

                    MangasPage(listOf(manga), false)
                }
        }

        // Special characters causing search to fail
        val queryFixed = query
            .replace("–", "-")
            .replace("’", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("…", "...")

        return super.fetchSearchManga(page, queryFixed, filters)
    }

    private val oldMangaUrlRegex = Regex("^$baseUrl/\\w+/")

    // Change old entries from mangaSubString
    override fun getMangaUrl(manga: SManga): String {
        return super.getMangaUrl(manga)
            .replace(oldMangaUrlRegex, "$baseUrl/$mangaSubString/")
    }

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).distinctBy { it.imageUrl }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
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
        }.let(screen::addPreference)
    }

    companion object {
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng thay đổi."
        private const val BASE_URL_PREF_TITLE = "Ghi đè URL cơ sở"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY =
            "Dành cho sử dụng tạm thời, cập nhật tiện ích sẽ xóa cài đặt."
    }
}
