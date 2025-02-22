package eu.kanade.tachiyomi.extension.en.shootingstarscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ShootingStarScans : Madara("Shooting Star Scans", "https://shootingstarscans.com", "en")
