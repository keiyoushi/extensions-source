package eu.kanade.tachiyomi.extension.ja.jmanga

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.getPreferencesLazy
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class Jmanga :
    MangaReader("Jmanga", "https://jmanga.codes", "ja"),
    ConfigurableSource {

    private val defaultBaseUrl = "https://jmanga.codes"

    private var cachedBaseUrl: String = ""
    private val SharedPreferences.prefBaseUrl: String get() {
        return cachedBaseUrl.takeIf(String::isNotBlank)
            ?: getString(BASE_URL_PREF_KEY, defaultBaseUrl)!!.also { cachedBaseUrl = it }
    }

    private val preferences: SharedPreferences by getPreferencesLazy {
        getString(DEFAULT_BASE_URL_PREF, "").let { domain ->
            if (domain != defaultBaseUrl) {
                edit()
                    .putString(BASE_URL_PREF_KEY, defaultBaseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override val baseUrl: String get() = preferences.prefBaseUrl

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_KEY
            title = BASE_URL_PREF_TITLE
            summary = URL_PREF_SUMMARY
            setDefaultValue(defaultBaseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $defaultBaseUrl"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            } else {
                addPathSegment("filter")
                val filterList = filters.ifEmpty { getFilterList() }
                filterList.filterIsInstance<MangaReader.UriFilter>().forEach {
                    it.addToUri(this)
                }
            }
            addPage(page, this)
        }.build()

        return GET(url, headers)
    }

    companion object {
        private const val BASE_URL_PREF_KEY = "BASE_URL"
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
        private const val BASE_URL_PREF_TITLE = "Base URL"
        private const val URL_PREF_SUMMARY = "Change if the site moves to a new domain. Leave blank to use default URL"
        private const val RESTART_APP_MESSAGE = "Restart app to apply new setting."
    }
}
