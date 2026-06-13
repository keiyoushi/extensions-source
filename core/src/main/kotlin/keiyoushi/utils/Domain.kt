package keiyoushi.utils

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen

private const val DEFAULT_DOMAIN_PREF_KEY = "custom_domain"

/**
 * Returns the user-configured custom domain, or [default] if none has been set.
 *
 * Use this in the extension's `baseUrl` getter when paired with [addDomainPreference].
 *
 * @param default the value returned when no override has been stored
 * @param key the SharedPreferences key. Must match the one passed to [addDomainPreference]
 */
fun SharedPreferences.getDomain(
    default: String,
    key: String = DEFAULT_DOMAIN_PREF_KEY,
): String = getString(key, null)?.takeIf { it.isNotBlank() } ?: default

/**
 * Adds an [EditTextPreference] to the screen that lets the user override the source domain.
 *
 * The value is stored in [prefs] under [key] and can be read back with [getDomain].
 *
 * @param prefs the source's [SharedPreferences]
 * @param default the default domain shown as a hint when no override is set
 * @param key the SharedPreferences key. Override when an extension needs more than one
 *   custom-domain pref, or to migrate from a legacy key
 * @param title the preference title (defaults to "Custom domain")
 * @param restartMessage toast message displayed after the user changes the domain
 */
fun PreferenceScreen.addDomainPreference(
    prefs: SharedPreferences,
    default: String,
    key: String = DEFAULT_DOMAIN_PREF_KEY,
    title: String = "Custom domain",
    restartMessage: String = "Restart the app to apply the new domain.",
) {
    EditTextPreference(context).apply {
        this.key = key
        this.title = title
        dialogTitle = title
        summary = prefs.getDomain(default, key)
        setDefaultValue(default)
        setOnPreferenceChangeListener { pref, newValue ->
            val newDomain = (newValue as? String)?.trim().orEmpty()
            pref.summary = newDomain.ifBlank { default }
            Toast.makeText(context, restartMessage, Toast.LENGTH_LONG).show()
            true
        }
    }.let(::addPreference)
}
