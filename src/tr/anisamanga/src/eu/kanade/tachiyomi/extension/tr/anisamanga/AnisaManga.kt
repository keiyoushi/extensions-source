package eu.kanade.tachiyomi.extension.tr.anisamanga
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class AnisaManga : Madara("Anisa Manga", "https://anisamanga.com", "tr")
