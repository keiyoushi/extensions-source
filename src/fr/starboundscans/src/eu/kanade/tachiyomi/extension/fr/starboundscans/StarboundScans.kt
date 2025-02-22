package eu.kanade.tachiyomi.extension.fr.starboundscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class StarboundScans : Keyoapp("Starbound Scans", "https://starboundscans.com", "fr") {
    override val dateSelector = "[class='text-xs text-white/50 w-fit']"
}
