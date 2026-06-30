package keiyoushi.source

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import java.net.URI

class MirrorPreferences(
    private val preferences: SharedPreferences,
    private val mirrors: Array<String>,
    private val title: String,
) {
    private val prefKey = "preferred_mirror"
    private val entries = mirrors.map { url ->
        runCatching { URI(url).host }.getOrDefault(url)
    }

    val baseUrl: String
        get() {
            val index = preferences.getString(prefKey, "0")?.toIntOrNull() ?: 0
            val safeIndex = index.coerceIn(0, mirrors.size - 1)
            return mirrors[safeIndex]
        }

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = prefKey
            title = this@MirrorPreferences.title
            entries = this@MirrorPreferences.entries.toTypedArray()
            entryValues = Array(mirrors.size) { it.toString() }
            summary = "%s"
            setDefaultValue("0")
        }.also(screen::addPreference)
    }
}
