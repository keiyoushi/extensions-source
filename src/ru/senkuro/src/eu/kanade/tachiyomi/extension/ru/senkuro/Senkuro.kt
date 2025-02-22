package eu.kanade.tachiyomi.extension.ru.senkuro
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.senkuro.Senkuro

class Senkuro : Senkuro("Senkuro", "https://senkuro.com", "ru") {
    override fun setupPreferenceScreen(screen: PreferenceScreen) {}
}
