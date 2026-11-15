package eu.kanade.tachiyomi.extension.en.monochromecustom

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.monochrome.MonochromeCMS
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy

@Source
abstract class MonochromeCustom :
    MonochromeCMS(),
    ConfigurableSource {

    override val apiUrl: String
        get() = preferences.getString("apiUrl", DEMO_API_URL)!!

    private val preferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "apiUrl"
            title = "API URL"
            summary = "The API URL of your Monochrome installation"
            setDefaultValue(DEMO_API_URL)
        }.let(screen::addPreference)
    }

    companion object {
        private const val DEMO_API_URL = "https://api-3qnqyl7llq-lz.a.run.app"
    }
}
