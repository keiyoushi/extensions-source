package keiyoushi.source

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import java.net.URI

class MirrorPreferences(
    private val preferences: SharedPreferences,
    private val defaultMirrors: Array<Pair<String, String>>,
    private val title: String,
) : FetchableBaseUrl {
    private val prefKey = "preferred_mirror"
    private val fetchedKey = "fetchedMirrors"
    private val fetchedFromKey = "fetchedMirrorsFrom"
    private val hardcodedUrls = defaultMirrors.joinToString(",") { it.second }

    init {
        val fetchedFrom = preferences.getString(fetchedFromKey, null)
        if (fetchedFrom != null && fetchedFrom != hardcodedUrls) {
            preferences.edit()
                .remove(fetchedKey)
                .remove(fetchedFromKey)
                .remove(prefKey)
                .apply()
        }
    }

    private val mirrors: List<Pair<String, String>>
        get() = preferences.getString(fetchedKey, null)?.split(",")?.map { "" to it } ?: defaultMirrors.asList()

    private val index: Int
        get() = (preferences.getString(prefKey, "0")?.toIntOrNull() ?: 0).coerceIn(0, mirrors.size - 1)

    val baseUrl: String
        get() = mirrors[index].second

    override fun update(urls: List<String>) {
        // The default follows the site's order, a pick follows its url
        val newIndex = if (index == 0) 0 else urls.indexOf(baseUrl).coerceAtLeast(0)
        preferences.edit()
            .putString(fetchedKey, urls.joinToString(","))
            .putString(fetchedFromKey, hardcodedUrls)
            .putString(prefKey, newIndex.toString())
            .apply()
    }

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrors = mirrors
        if (mirrors.size < 2) return
        ListPreference(screen.context).apply {
            key = prefKey
            title = this@MirrorPreferences.title
            entries = mirrors.map { (label, url) ->
                label.takeIf { it.isNotBlank() }
                    ?: runCatching { URI(url).host }.getOrDefault(url)
            }.toTypedArray()
            entryValues = Array(mirrors.size) { it.toString() }
            summary = "%s"
            setDefaultValue("0")
        }.also(screen::addPreference)
    }
}
