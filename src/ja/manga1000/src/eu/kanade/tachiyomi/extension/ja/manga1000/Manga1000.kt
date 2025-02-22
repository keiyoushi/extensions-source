package eu.kanade.tachiyomi.extension.ja.manga1000
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.liliana.Liliana

class Manga1000 : Liliana("Manga1000", "https://manga1000.top", "ja")
