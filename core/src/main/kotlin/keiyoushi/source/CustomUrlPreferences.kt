package keiyoushi.source

import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class CustomUrlPreferences(
    private val preferences: SharedPreferences,
    private val defaultUrl: String,
) {
    private val prefBaseKey = "overrideBaseUrl"
    private val prefDefaultKey = "${prefBaseKey}_default"

    init {
        val storedDefault = preferences.getString(prefDefaultKey, null)
        if (storedDefault != defaultUrl) {
            preferences.edit()
                .putString(prefBaseKey, defaultUrl)
                .putString(prefDefaultKey, defaultUrl)
                .apply()
        }
    }

    val baseUrl: String
        get() = preferences.getString(prefBaseKey, defaultUrl) ?: defaultUrl

    fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = prefBaseKey
            title = "Custom base URL"
            dialogTitle = "Custom base URL"
            dialogMessage = "Leave blank to use default"

            val currentValue = preferences.getString(prefBaseKey, null)
            summary = if (currentValue.isNullOrBlank() || currentValue == defaultUrl) {
                "Using default: $defaultUrl"
            } else {
                currentValue
            }
            setDefaultValue(defaultUrl)

            setOnBindEditTextListener { editText ->
                editText.hint = defaultUrl
                editText.setHorizontallyScrolling(true)
                editText.post { editText.selectAll() }
                editText.addTextChangedListener(
                    object : TextWatcher {
                        override fun afterTextChanged(editable: Editable?) {
                            val text = editable?.toString() ?: ""
                            val isValid = text.isBlank() || text.toHttpUrlOrNull() != null

                            editText.error = if (isValid) null else "Invalid URL"

                            // Disable the "OK" button if invalid
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
                        ""
                    } else {
                        val scheme = httpUrl!!.scheme
                        val host = httpUrl.host
                        val port = httpUrl.port
                        val defaultPort = if (scheme == "https") 443 else 80
                        val portStr = if (port == defaultPort) "" else ":$port"
                        "$scheme://$host$portStr"
                    }

                    preferences.edit()
                        .putString(prefBaseKey, sanitizedValue.ifBlank { defaultUrl })
                        .apply()

                    (preference as EditTextPreference).text = sanitizedValue
                    summary = sanitizedValue.ifBlank { "Using default: $defaultUrl" }
                }
                false
            }
        }.also(screen::addPreference)
    }
}
