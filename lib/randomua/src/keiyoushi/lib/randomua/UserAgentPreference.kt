package keiyoushi.lib.randomua

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferences
import okhttp3.Headers

/**
 * Helper function to return UserAgentType based on SharedPreference value
 */
private fun SharedPreferences.getPrefUAType(): UserAgentType = when (getString(PREF_KEY_RANDOM_UA, "off")) {
    "mobile" -> UserAgentType.MOBILE
    "desktop" -> UserAgentType.DESKTOP
    else -> UserAgentType.OFF
}

/**
 * Helper function to return custom UserAgent from SharedPreference
 */
private fun SharedPreferences.getPrefCustomUA(): String? = getString(PREF_KEY_CUSTOM_UA, null)
    ?.takeIf { it.isNotBlank() }

/**
 * Helper function to add user agent preference to the headers
 *
 * @param userAgentType only set if you want to not include or bypass the preference value
 * @param filterInclude Filter to only include Random User Agents containing these strings
 * @param filterExclude Filter to exclude Random User Agents containing these strings
 */
context(source: HttpSource)
fun Headers.Builder.setRandomUserAgent(
    userAgentType: UserAgentType? = null,
    filterInclude: List<String> = emptyList(),
    filterExclude: List<String> = emptyList(),
) = apply {
    val preferences = source.getPreferences()
    val randomUserAgentType = userAgentType ?: preferences.getPrefUAType()
    val customUserAgent = preferences.getPrefCustomUA()

    val userAgent = if (randomUserAgentType != UserAgentType.OFF) {
        getRandomUserAgent(randomUserAgentType, filterInclude, filterExclude)
            ?: return@apply
    } else if (customUserAgent != null) {
        customUserAgent
    } else {
        return@apply
    }

    set("User-Agent", userAgent)
}

/**
 * Helper function to add Random User-Agent settings to SharedPreference
 */
context(source: HttpSource)
fun PreferenceScreen.addRandomUAPreference() {
    val preferences = source.getPreferences()
    val customUaPref = EditTextPreference(context).apply {
        key = PREF_KEY_CUSTOM_UA
        title = "Custom user agent string"
        summary = "Leave blank to use the default user agent string"
        setEnabled(preferences.getPrefUAType() == UserAgentType.OFF)
        setOnPreferenceChangeListener { _, newValue ->
            try {
                Headers.headersOf("User-Agent", newValue as String)
                Toast.makeText(context, "Restart the app to apply changes", Toast.LENGTH_SHORT).show()
                true
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, "Invalid user agent string: ${e.message}", Toast.LENGTH_LONG).show()
                false
            }
        }
    }
    ListPreference(context).apply {
        key = PREF_KEY_RANDOM_UA
        title = "Random user agent string"
        entries = RANDOM_UA_ENTRIES
        entryValues = RANDOM_UA_VALUES
        summary = "%s"
        setDefaultValue("off")
        setOnPreferenceChangeListener { _, newValue ->
            customUaPref.setEnabled(newValue == "off")
            Toast.makeText(context, "Restart the app to apply changes", Toast.LENGTH_SHORT).show()
            true
        }
    }.also(::addPreference)
    customUaPref.also(::addPreference)
}

private const val PREF_KEY_RANDOM_UA = "pref_key_random_ua_"
private val RANDOM_UA_ENTRIES = arrayOf("OFF", "Desktop", "Mobile")
private val RANDOM_UA_VALUES = arrayOf("off", "desktop", "mobile")
private const val PREF_KEY_CUSTOM_UA = "pref_key_custom_ua_"
