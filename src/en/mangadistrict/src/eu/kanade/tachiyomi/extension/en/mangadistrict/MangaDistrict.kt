package eu.kanade.tachiyomi.extension.en.mangadistrict

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangaDistrict :
    Madara(
        "Manga District",
        "https://mangadistrict.com",
        "en",
    ),
    ConfigurableSource {

    override val mangaSubString = "read-scan"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaNextPageSelector() = "div[role=navigation] span.current + a.page"

    private val titleVersion = Regex("\\(.*\\)")

    override fun mangaDetailsParse(document: Document): SManga {
        val tags = document.select(mangaDetailsSelectorTag).mapNotNull { element ->
            element.ownText() to element.attr("href")
                .removeSuffix("/").substringAfterLast('/')
        }
        tagList = tagList.plus(tags)

        return super.mangaDetailsParse(document).apply {
            if (isRemoveTitleVersion()) {
                title = this.title.replace(titleVersion, "").trim()
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)
        return when (getImgRes()) {
            IMG_RES_HIGH -> chapters.filterNot { it.url.contains("/v2-full-quality") }
            IMG_RES_FULL -> chapters.filterNot { it.url.contains("/v1-high-quality") }
            else -> chapters
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            val urlKey = url.urlKey()
            preferences.dates[urlKey]?.also {
                date_upload = it
            }
        }
    }

    private val pageListDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun pageListParse(document: Document): List<Page> {
        try {
            pageListDate.parse(
                document.selectFirst("meta[property=og:updated_time]")!!
                    .attr("content").substringBeforeLast("+"),
            )!!.time.also {
                val dates = preferences.dates
                val urlKey = document.location().urlKey()
                dates[urlKey] = it
                preferences.dates = dates
            }
        } catch (_: Exception) {}

        return super.pageListParse(document)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val tagFilter = filters.filterIsInstance<TagList>().firstOrNull()
        if (tagFilter != null && tagFilter.state > 0) {
            val urlBuilder = baseUrl.toHttpUrl().newBuilder()
            urlBuilder.addPathSegment("publication-tag")
            urlBuilder.addPathSegment(tagFilter.toUriPart())
            urlBuilder.addPathSegments("page/$page")
            return client.newCall(GET(urlBuilder.build(), headers))
                .asObservableSuccess().map { response ->
                    popularMangaParse(response)
                }
        } else {
            return super.fetchSearchManga(page, query, filters)
        }
    }

    private fun loadTagListFromPreferences(): Set<Pair<String, String>> =
        preferences.getStringSet(TAG_LIST_PREF, emptySet())
            ?.mapNotNull {
                it.split('|')
                    .let { splits ->
                        if (splits.size == 2) {
                            splits[0] to splits[1]
                        } else {
                            null
                        }
                    }
            }
            ?.toSet()
            // Create at least 1 tag to avoid excessively reading preferences
            .let { if (it.isNullOrEmpty()) setOf("Manhwa" to "manhwa") else it }

    private var tagList: Set<Pair<String, String>> = loadTagListFromPreferences()
        set(value) {
            preferences.edit().putStringSet(
                TAG_LIST_PREF,
                value.map { "${it.first}|${it.second}" }.toSet(),
            ).apply()
            field = value
        }

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().list.toMutableList()
        if (tagList.isNotEmpty()) {
            filters += Filter.Separator()
            filters += Filter.Header("Tag browse will ignore other filters")
            filters += TagList("Tag browse", listOf(Pair("<Browse tag>", "")) + tagList.toList())
        }
        return FilterList(filters)
    }

    private class TagList(title: String, options: List<Pair<String, String>>, state: Int = 0) :
        UriPartFilter(title, options.toTypedArray(), state)

    private fun String.urlKey(): String {
        return toHttpUrl().pathSegments.let { path ->
            "${path[1]}/${path[2]}"
        }
    }

    private fun isRemoveTitleVersion() = preferences.getBoolean(REMOVE_TITLE_VERSION_PREF, false)
    private fun getImgRes() = preferences.getString(IMG_RES_PREF, IMG_RES_DEFAULT)!!

    private var SharedPreferences.dates: MutableMap<String, Long>
        get() = try {
            json.decodeFromString(getString(DATE_MAP, "{}") ?: "{}")
        } catch (_: Exception) {
            mutableMapOf()
        }

        @SuppressLint("ApplySharedPref")
        set(newVal) {
            edit().putString(DATE_MAP, json.encodeToString(newVal)).commit()
        }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_TITLE_VERSION_PREF
            title = "Remove version information from entry titles"
            summary = "This removes version tags like “(Official)” or “(Doujinshi)” from entry titles " +
                "and helps identify duplicate entries in your library. " +
                "To update existing entries, remove them from your library (unfavorite) and refresh manually. " +
                "You might also want to clear the database in advanced settings."
            setDefaultValue(false)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = IMG_RES_PREF
            title = "Image quality"
            entries = arrayOf("All", "High quality", "Full quality")
            entryValues = arrayOf(IMG_RES_ALL, IMG_RES_HIGH, IMG_RES_FULL)
            summary = "%s\nRefresh entry to update the chapter list."
            setDefaultValue(IMG_RES_DEFAULT)
        }.let(screen::addPreference)
    }

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-wpfc-original-src") -> element.attr("abs:data-wpfc-original-src")
            else -> super.imageFromElement(element)
        }
    }

    companion object {
        private const val REMOVE_TITLE_VERSION_PREF = "REMOVE_TITLE_VERSION"
        private const val TAG_LIST_PREF = "TAG_LIST"

        private const val IMG_RES_PREF = "IMG_RES"
        private const val IMG_RES_ALL = "all"
        private const val IMG_RES_HIGH = "high"
        private const val IMG_RES_FULL = "full"
        private const val IMG_RES_DEFAULT = IMG_RES_ALL

        private const val DATE_MAP = "date_saved"
    }
}
