package eu.kanade.tachiyomi.lib.randomua

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import okhttp3.Headers


/**
 * Helper function to return UserAgentType based on SharedPreference value
 */
fun SharedPreferences.getPrefUAType(): UserAgentType {
    return when (getString(PREF_KEY_RANDOM_UA, "off")) {
        "mobile" -> UserAgentType.MOBILE
        "desktop" -> UserAgentType.DESKTOP
        else -> UserAgentType.OFF
    }
}

/**
 * Helper function to return custom UserAgent from SharedPreference
 */
fun SharedPreferences.getPrefCustomUA(): String? {
    return getString(PREF_KEY_CUSTOM_UA, null)
}

/**
 * Helper function to add Random User-Agent settings to SharedPreference
 *
 * @param screen, PreferenceScreen from `setupPreferenceScreen`
 */
fun addRandomUAPreferenceToScreen(
    screen: PreferenceScreen,
) {
    val context = screen.context

    ListPreference(context).apply {
        key = PREF_KEY_RANDOM_UA
        title = TITLE_RANDOM_UA
        entries = RANDOM_UA_ENTRIES
        entryValues = RANDOM_UA_VALUES
        summary = "%s"
        setDefaultValue("off")
    }.also(screen::addPreference)

    EditTextPreference(context).apply {
        key = PREF_KEY_CUSTOM_UA
        title = TITLE_CUSTOM_UA
        summary = CUSTOM_UA_SUMMARY
        setOnPreferenceChangeListener { _, newValue ->
            try {
                Headers.headersOf("User-Agent", newValue as String)
                true
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, "Invalid user agent string: ${e.message}", Toast.LENGTH_LONG).show()
                false
            }
        }
    }.also(screen::addPreference)
}

const val TITLE_RANDOM_UA = "Random user agent string (requires restart)"
const val PREF_KEY_RANDOM_UA = "pref_key_random_ua_"
val RANDOM_UA_ENTRIES = arrayOf("OFF", "Desktop", "Mobile")
val RANDOM_UA_VALUES = arrayOf("off", "desktop", "mobile")

const val TITLE_CUSTOM_UA = "Custom user agent string (requires restart)"
const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua_"
const val CUSTOM_UA_SUMMARY = "Leave blank to use the default user agent string (ignored if random user agent string is enabled)"
