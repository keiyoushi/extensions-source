package keiyoushi.source

import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

class CustomAndMirrorPreferences(
    private val preferences: SharedPreferences,
    private val mirrors: List<String>,
    val defaultUrl: String,
    private val withCustom: Boolean,
    private val mirrorTitle: String,
    private val customTitle: String,
    private val customDialogMessage: String,
    mirrorEntries: List<String>? = null,
    mirrorEntryValues: List<String>? = null,
) {
    private val prefKey: String = "preferred_mirror"
    private val prefBaseKey: String = "overrideBaseUrl"
    private val prefDefaultKey: String = "defaultBaseUrl"

    private val entries = (
        mirrorEntries ?: mirrors.map { url ->
            runCatching { URI(url).host }.getOrDefault(url)
        }
        ).run { if (this@CustomAndMirrorPreferences.withCustom) plus("Custom") else this }

    private val entryValues = (
        mirrorEntryValues ?: mirrors.indices.map { it.toString() }
        ).run { if (this@CustomAndMirrorPreferences.withCustom) plus("custom") else this }

    init {
        if (withCustom) {
            val storedDefault = preferences.getString(prefDefaultKey, null)
            if (storedDefault != defaultUrl) {
                preferences.edit()
                    .putString(prefBaseKey, defaultUrl)
                    .putString(prefDefaultKey, defaultUrl)
                    .apply()
            }
        }
    }

    val baseUrl: String
        get() {
            val selected = preferences.getString(prefKey, entryValues.firstOrNull() ?: "0")
            val url = if (selected == "custom") {
                preferences.getString(prefBaseKey, defaultUrl) ?: defaultUrl
            } else {
                val index = entryValues.indexOf(selected).coerceIn(0, mirrors.size - 1)
                mirrors.getOrNull(index) ?: defaultUrl
            }
            return url.removeSuffix("/")
        }

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        val customUrlPref = if (withCustom) {
            EditTextPreference(screen.context).apply {
                key = prefBaseKey
                title = customTitle
                dialogTitle = customTitle
                dialogMessage = customDialogMessage

                val currentValue = preferences.getString(prefBaseKey, null)
                summary = currentValue?.takeIf { it.isNotBlank() } ?: defaultUrl
                setDefaultValue(defaultUrl)

                setEnabled(preferences.getString(prefKey, entryValues.firstOrNull()) == "custom")

                setOnBindEditTextListener { editText ->
                    editText.hint = defaultUrl
                    editText.setHorizontallyScrolling(true)
                    editText.post { editText.selectAll() }
                    editText.addTextChangedListener(
                        object : TextWatcher {
                            override fun afterTextChanged(editable: Editable?) {
                                val text = editable?.toString() ?: ""
                                val isValid = text.isBlank() || text.toHttpUrlOrNull() != null
                                editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = isValid
                            }

                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        },
                    )
                }

                setOnPreferenceChangeListener { preference, newValue ->
                    val text = newValue as String
                    val httpUrl = text.toHttpUrlOrNull()
                    val isValid = text.isBlank() || httpUrl != null
                    if (isValid) {
                        val sanitizedValue = if (text.isBlank()) {
                            defaultUrl
                        } else {
                            val httpUrlParsed = httpUrl!!
                            val scheme = httpUrlParsed.scheme
                            val host = httpUrlParsed.host
                            val port = httpUrlParsed.port
                            val defaultPort = if (scheme == "https") 443 else 80
                            val portStr = if (port == defaultPort) "" else ":$port"
                            "$scheme://$host$portStr"
                        }

                        (preference as EditTextPreference).text = sanitizedValue
                        summary = sanitizedValue

                        preferences.edit()
                            .putString(prefBaseKey, sanitizedValue)
                            .apply()
                    }
                    false
                }
            }
        } else {
            null
        }

        ListPreference(screen.context).apply {
            key = prefKey
            title = mirrorTitle
            entries = this@CustomAndMirrorPreferences.entries.toTypedArray()
            entryValues = this@CustomAndMirrorPreferences.entryValues.toTypedArray()
            summary = "%s"
            setDefaultValue(this@CustomAndMirrorPreferences.entryValues.firstOrNull() ?: "0")

            setOnPreferenceChangeListener { _, newValue ->
                customUrlPref?.setEnabled(newValue == "custom")
                true
            }
        }.also(screen::addPreference)

        customUrlPref?.also(screen::addPreference)
    }
}
