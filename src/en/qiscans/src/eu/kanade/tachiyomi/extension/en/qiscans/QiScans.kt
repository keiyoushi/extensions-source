package eu.kanade.tachiyomi.extension.en.qiscans

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwa
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaChapterDto
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaSortFilter
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaStatusFilter
import eu.kanade.tachiyomi.multisrc.ezmanhwa.EZManhwaTypeFilter
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class QiScans :
    EZManhwa("QiScans", "https://qimanhwa.com"),
    ConfigurableSource {

    override val apiUrl = "https://api.qimanhwa.com/api/v1"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("Sec-Fetch-Dest", "empty")
        .set("Sec-Fetch-Mode", "cors")
        .set("Sec-Fetch-Site", "same-site")

    // Logic: If user types text, search using /search (ignores filters).
    // If text is empty, browse using /series (applies filters).
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val isSearch = query.isNotBlank()
        val endpoint = if (isSearch) "$apiUrl/series/search" else "$apiUrl/series"
        val url = endpoint.toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "20")
            if (isSearch) {
                addQueryParameter("q", query)
            } else {
                for (filter in filters) {
                    when (filter) {
                        is EZManhwaSortFilter -> addQueryParameter("sort", filter.value)
                        is EZManhwaStatusFilter -> if (filter.value.isNotBlank()) addQueryParameter("status", filter.value)
                        is EZManhwaTypeFilter -> if (filter.value.isNotBlank()) addQueryParameter("type", filter.value)
                        is QiScansGenreFilter -> if (filter.value.isNotBlank()) addQueryParameter("genre", filter.value)
                        else -> {}
                    }
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun shouldShowChapter(chapter: EZManhwaChapterDto): Boolean {
        val showLocked = preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false)
        return showLocked || chapter.requiresPurchase != true
    }

    override fun getFilterList() = FilterList(
        EZManhwaSortFilter(),
        EZManhwaStatusFilter(),
        EZManhwaTypeFilter(),
        QiScansGenreFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTER_PREF_KEY
            title = "Show locked chapters"
            summary = "Show chapters requiring coins. Note: They only load if owned/logged in via webview."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val SHOW_LOCKED_CHAPTER_PREF_KEY = "pref_show_locked_chapters"
    }
}
