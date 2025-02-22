package eu.kanade.tachiyomi.extension.en.monochromescans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.monochrome.MonochromeCMS

class MonochromeScans : MonochromeCMS("Monochrome Scans", "https://manga.d34d.one", "en")
