package eu.kanade.tachiyomi.extension.en.monochromecustom

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.monochrome.MonochromeCMS
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.utils.getPreferencesLazy

class MonochromeCustom : ConfigurableSource,
    MonochromeCMS("Monochrome Custom", "", "en") {
    override val baseUrl by lazy {
        preferences.getString("baseUrl", DEMO_BASE_URL)!!
    }

    override val apiUrl by lazy {
        preferences.getString("apiUrl", DEMO_API_URL)!!
    }

    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "baseUrl"
            title = "Frontend URL"
            summary = "The base URL of your Monochrome installation"
            setDefaultValue(DEMO_BASE_URL)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("baseUrl", newValue as String).commit()
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = "apiUrl"
            title = "API URL"
            summary = "The API URL of your Monochrome installation"
            setDefaultValue(DEMO_API_URL)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("apiUrl", newValue as String).commit()
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val DEMO_BASE_URL = "https://monochromecms.netlify.app"

        private const val DEMO_API_URL = "https://api-3qnqyl7llq-lz.a.run.app"
    }
}
