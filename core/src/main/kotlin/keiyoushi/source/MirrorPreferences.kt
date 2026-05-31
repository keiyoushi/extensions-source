package keiyoushi.source

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen

class MirrorPreferences(
    private val preferences: SharedPreferences,
    private val mirrors: Array<Pair<String, String>>,
    private val prefKey: String = "preferred_mirror",
) {
    val baseUrl: String
        get() {
            val index = preferences.getString(prefKey, "0")?.toIntOrNull() ?: 0
            val safeIndex = index.coerceIn(0, mirrors.size - 1)
            return mirrors[safeIndex].second
        }

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = prefKey
            title = "Preferred mirror"
            entries = mirrors.map { it.first }.toTypedArray()
            entryValues = Array(mirrors.size) { it.toString() }
            summary = "%s"
            setDefaultValue("0")
        }.also(screen::addPreference)
    }
}
