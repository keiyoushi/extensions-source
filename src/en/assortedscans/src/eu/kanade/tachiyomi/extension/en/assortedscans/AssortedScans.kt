package eu.kanade.tachiyomi.extension.en.assortedscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangadventure.MangAdventure

class AssortedScans : MangAdventure("Assorted Scans", "https://assortedscans.com", "en")
