package eu.kanade.tachiyomi.extension.fr.bananascan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class HarmonyScan : Madara("Harmony-Scan", "https://harmony-scan.fr", "fr") {
    // formally Banana-Scan
    override val id = 3121632933690925888
}
