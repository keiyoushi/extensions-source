package keiyoushi.source

import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen

class CustomUrlPreferences(
    private val preferences: SharedPreferences,
    private val defaultUrl: String,
    private val prefBaseKey: String = "overrideBaseUrl",
    private val prefDefaultKey: String = "defaultBaseUrl",
) {
    // Strict Regex: https?://domain.tld (no trailing slash, no path)
    private val urlRegex = Regex("^https?://[^/?#]+\\.[^/?#]+$")

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
            summary = "Default: $defaultUrl"
            setDefaultValue(defaultUrl)

            setOnBindEditTextListener { editText ->
                editText.addTextChangedListener(
                    object : TextWatcher {
                        override fun afterTextChanged(editable: Editable?) {
                            val text = editable?.toString() ?: ""
                            val isValid = text.isBlank() || urlRegex.matches(text)

                            editText.error = if (isValid) null else "Invalid URL (use https://domain.tld)"

                            // Disable the "OK" button if invalid
                            editText.rootView.findViewById<Button>(android.R.id.button1)?.isEnabled = isValid
                        }

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    },
                )
            }

            setOnPreferenceChangeListener { _, newValue ->
                val text = newValue as String
                val isValid = text.isBlank() || urlRegex.matches(text)
                if (isValid) {
                    summary = if (text.isBlank()) "Using default: $defaultUrl" else text
                }
                isValid
            }
        }.also(screen::addPreference)
    }
}
