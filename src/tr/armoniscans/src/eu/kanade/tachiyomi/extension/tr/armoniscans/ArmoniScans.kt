package eu.kanade.tachiyomi.extension.tr.armoniscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ArmoniScans : Madara("Armoni Scans", "https://armoniscans.net", "tr")
