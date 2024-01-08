package eu.kanade.tachiyomi.extension.ko.toonkor

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import eu.kanade.tachiyomi.AppInfo
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale

class Toonkor : ConfigurableSource, ParsedHttpSource() {

    override val name = "Toonkor"

    private val defaultBaseUrl = "https://tkor.dog"

    private val BASE_URL_PREF = "overrideBaseUrl_v${AppInfo.getVersionName()}"

    override val baseUrl by lazy { getPrefBaseUrl() }

    override val lang = "ko"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    private val webtoonsRequestPath = "/%EC%9B%B9%ED%88%B0"

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl + webtoonsRequestPath, headers)
    }

    override fun popularMangaSelector() = "div.section-item-inner"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.section-item-title a").let {
                title = it.select("h3").text()
                url = it.attr("href")
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    private val latestRequestModifier = "?fil=%EC%B5%9C%EC%8B%A0"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl + webtoonsRequestPath + latestRequestModifier, headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        // Webtoons, Manga, or Hentai
        val type = filterList.findUriPartFilter<TypeFilter>()
        // Popular, Latest, or Completed
        val sort = filterList.findUriPartFilter<SortFilter>()

        // Hentai doesn't have a "completed" sort, ignore it if it's selected (equivalent to returning popular)
        val requestPath = when {
            query.isNotBlank() -> "/bbs/search.php?sfl=wr_subject%7C%7Cwr_content&stx=$query"
            type.isSelection("Hentai") && sort.isSelection("Completed") -> type.toUriPart()
            else -> type.toUriPart() + sort.toUriPart()
        }

        return GET(baseUrl + requestPath, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
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

    override fun chapterListSelector() = "table.web_list tr:has(td.content__title)"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("td.content__title").let {
                url = it.attr("data-role")
                name = it.text()
            }
            date_upload = element.select("td.episode__index").text().toDate()
        }
    }

    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    private fun String.toDate(): Long {
        return dateFormat.parse(this)?.time ?: 0
    }

    // Pages

    private val pageListRegex = Regex("""src="([^"]*)"""")

    override fun pageListParse(document: Document): List<Page> {
        val encoded = document.select("script:containsData(toon_img)").firstOrNull()?.data()
            ?.substringAfter("'")?.substringBefore("'") ?: throw Exception("toon_img script not found")

        val decoded = Base64.decode(encoded, Base64.DEFAULT).toString(Charset.defaultCharset())

        return pageListRegex.findAll(decoded).toList().mapIndexed { i, matchResult ->
            Page(i, "", matchResult.destructured.component1().let { if (it.startsWith("http")) it else baseUrl + it })
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Note: can't combine with text search!"),
            Filter.Separator(),
            TypeFilter(getTypeList()),
            SortFilter(getSortList()),
        )
    }

    private class TypeFilter(vals: Array<Pair<String, String>>) : UriPartFilter("Type", vals)
    private class SortFilter(vals: Array<Pair<String, String>>) : UriPartFilter("Sort", vals)

    private fun getTypeList() = arrayOf(
        Pair("Webtoons", webtoonsRequestPath),
        Pair("Manga", "/%EB%8B%A8%ED%96%89%EB%B3%B8"),
        Pair("Hentai", "/%EB%A7%9D%EA%B0%80"),
    )

    private fun getSortList() = arrayOf(
        Pair("Popular", ""),
        Pair("Latest", latestRequestModifier),
        Pair("Completed", "/%EC%99%84%EA%B2%B0"),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun isSelection(name: String): Boolean = name == vals[state].first
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified T> FilterList.findUriPartFilter(): UriPartFilter = this.find { it is T } as UriPartFilter

    // Preferences

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    companion object {
        private const val BASE_URL_PREF_TITLE = "Override BaseUrl"
        private const val BASE_URL_PREF_SUMMARY = "Override default domain with a different one"
    }
}
