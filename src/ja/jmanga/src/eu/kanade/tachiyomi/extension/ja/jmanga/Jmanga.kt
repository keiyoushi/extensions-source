package eu.kanade.tachiyomi.extension.ja.jmanga

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

    private val preferences by getPreferencesLazy()

    private val baseDefaultUrl = "https://jmanga.codes"
    override val baseUrl: String get() = preferences.getString(BASE_URL_PREF_KEY, baseDefaultUrl)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_KEY
            title = "Base URL"
            summary = "Change if the site moves to a new domain. Default: $baseDefaultUrl"
            setDefaultValue(baseDefaultUrl)
            dialogTitle = "Base URL"
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
    }
}
