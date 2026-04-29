package eu.kanade.tachiyomi.extension.ko.toonkor

import android.content.SharedPreferences
import android.util.Base64
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

class Toonkor :
    HttpSource(),
    ConfigurableSource {

    override val name = "Toonkor"

    private val defaultBaseUrl = "https://tkor114.com"

    private val baseUrlPref = "overrideBaseUrl_v${AppInfo.getVersionName()}"

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val lang = "ko"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val webtoonsRequestPath = "/%EC%9B%B9%ED%88%B0"
    private val latestRequestModifier = "?fil=%EC%B5%9C%EC%8B%A0"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val pageListRegex = Regex("""src="([^"]*)"""")

    private val preferences: SharedPreferences by getPreferencesLazy()

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl + webtoonsRequestPath, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.section-item-inner").map { element ->
            SManga.create().apply {
                element.select("div.section-item-title a").let {
                    title = it.select("h3").text()
                    setUrlWithoutDomain(it.attr("abs:href"))
                }
                thumbnail_url = element.select("img").attr("abs:src")
            }
        }

        return MangasPage(mangas, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl + webtoonsRequestPath + latestRequestModifier, headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val type = filterList.firstInstanceOrNull<TypeFilter>()
        val sort = filterList.firstInstanceOrNull<SortFilter>()

        // Hentai doesn't have a "completed" sort, ignore it if it's selected (equivalent to returning popular)
        val requestPath = when {
            query.isNotBlank() -> "/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&stx=$query"
            type?.isSelection("Hentai") == true && sort?.isSelection("Completed") == true -> type.toUriPart()
            else -> (type?.toUriPart() ?: "") + (sort?.toUriPart() ?: "")
        }

        return GET(baseUrl + requestPath, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            with(document.select("table.bt_view1")) {
                title = select("td.bt_title").text()
                author = select("td.bt_label span.bt_data").text()
                description = select("td.bt_over").text()
                thumbnail_url = select("td.bt_thumb img").firstOrNull()?.attr("abs:src")
            }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("table.web_list tr:has(td.content__title)").map { element ->
            SChapter.create().apply {
                element.select("td.content__title").let {
                    url = it.attr("data-role")
                    name = it.text()
                }
                date_upload = dateFormat.tryParse(element.select("td.episode__index").text())
            }
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val encoded = document.select("script:containsData(toon_img)").firstOrNull()?.data()
            ?.substringAfter("'")?.substringBefore("'") ?: throw Exception("toon_img script not found")

        val decoded = Base64.decode(encoded, Base64.DEFAULT).toString(Charset.defaultCharset())

        return pageListRegex.findAll(decoded).mapIndexed { i, matchResult ->
            val imageUrl = matchResult.destructured.component1().let { if (it.startsWith("http")) it else baseUrl + it }
            Page(i, imageUrl = imageUrl)
        }.toList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: can't combine with text search!"),
        Filter.Separator(),
        TypeFilter(),
        SortFilter(),
    )

    // Preferences

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = this@Toonkor.baseUrlPref
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"
        }

        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(baseUrlPref, defaultBaseUrl)!!

    companion object {
        private const val BASE_URL_PREF_TITLE = "Override BaseUrl"
        private const val BASE_URL_PREF_SUMMARY = "Override default domain with a different one"
    }
}
