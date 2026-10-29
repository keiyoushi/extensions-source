package keiyoushi.source

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class FetchedUrlPreferences(
    private val preferences: SharedPreferences,
    private val fallbackUrls: List<String>,
    private val title: String,
    private val automatic: String,
) {
    private val urls: List<String>
        get() = preferences.getString(LIST_KEY, null)
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            .orEmpty()
            .ifEmpty { fallbackUrls }

    val baseUrl: String
        get() {
            val urls = urls
            return preferences.getString(PICKED_KEY, null)?.takeIf { it in urls } ?: urls.first()
        }

    /** Fetches the urls in the background, once per launch. */
    @OptIn(DelicateCoroutinesApi::class)
    internal fun refresh(fetch: suspend () -> List<String>) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val urls = fetch()
                    .mapNotNull { it.toHttpUrlOrNull()?.resolve("/")?.toString()?.removeSuffix("/") }
                    .distinct()
                if (urls.isNotEmpty()) {
                    preferences.edit().putString(LIST_KEY, urls.joinToString(",")).apply()
                }
            } catch (e: Exception) {
                Log.e("FetchedUrls", "Failed to fetch base urls", e)
            }
        }
    }

    /** Picker when the site lists more than one url. */
    fun setupPreferenceScreen(screen: PreferenceScreen) {
        val urls = urls
        if (urls.size < 2) return
        ListPreference(screen.context).apply {
            key = PICKED_KEY
            title = this@FetchedUrlPreferences.title
            entries = arrayOf("$automatic (${urls.first().toHttpUrl().host})") + urls.map { it.toHttpUrl().host }
            entryValues = arrayOf("") + urls
            summary = "%s"
            setDefaultValue("")
        }.also(screen::addPreference)
    }

    private companion object {
        const val PICKED_KEY = "fetchedUrl"
        const val LIST_KEY = "fetchedUrls"
    }
}
