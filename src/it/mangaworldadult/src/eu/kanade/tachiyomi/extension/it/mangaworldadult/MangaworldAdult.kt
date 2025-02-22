package eu.kanade.tachiyomi.extension.it.mangaworldadult
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangaworld.MangaWorld

class MangaworldAdult : MangaWorld("MangaworldAdult", "https://www.mangaworldadult.net", "it")
