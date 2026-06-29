package eu.kanade.tachiyomi.extension.en.kaliscancom

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madtheme.MadTheme
import eu.kanade.tachiyomi.source.ConfigurableSource
import keiyoushi.utils.getPreferencesLazy

class KaliScanCom :
    MadTheme("KaliScan", "https://kaliscan.com", "en"),
    ConfigurableSource {

    override val id: Long = 7660637864742395387

    override val useLegacyApi = true

    private val preferences by getPreferencesLazy()

    override val baseUrl: String
        get() {
            val index = preferences.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE)?.toIntOrNull() ?: 0
            return MIRRORS[index.coerceIn(MIRRORS.indices)]
        }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = "Mirror URL"
            summary = "Select the domain to use."
            entries = MIRRORS
            entryValues = Array(MIRRORS.size) { it.toString() }
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
        }
        screen.addPreference(mirrorPref)
    }

    companion object {
        private const val MIRROR_PREF_KEY = "MIRROR_PREF_KEY"
        private const val MIRROR_PREF_DEFAULT_VALUE = "0"
        private val MIRRORS = arrayOf(
            "https://kaliscan.com",
            "https://kaliscan.me",
            "https://kaliscan.io",
            "https://mgjinx.com",
        )
    }
}
