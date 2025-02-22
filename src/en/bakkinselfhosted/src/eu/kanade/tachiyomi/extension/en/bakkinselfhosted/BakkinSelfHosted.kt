package eu.kanade.tachiyomi.extension.en.bakkinselfhosted

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.bakkin.BakkinReaderX

class BakkinSelfHosted : BakkinReaderX("Bakkin Self-hosted", "", "en") {
    override val baseUrl by lazy {
        preferences.getString("baseUrl", "http://127.0.0.1/")!!
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        EditTextPreference(screen.context).apply {
            key = "baseUrl"
            title = "Custom URL"
            summary = "Connect to a self-hosted Bakkin Reader X server"
            setDefaultValue("http://127.0.0.1/")

            setOnPreferenceChangeListener { _, newValue ->
                // Make sure the URL ends with one slash
                val url = (newValue as String).trimEnd('/') + '/'
                preferences.edit().putString("baseUrl", url).commit()
            }
        }.let(screen::addPreference)
    }
}
