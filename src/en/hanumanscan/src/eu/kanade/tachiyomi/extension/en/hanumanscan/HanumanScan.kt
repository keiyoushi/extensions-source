package eu.kanade.tachiyomi.extension.en.hanumanscan
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class HanumanScan : MangaThemesia("Hanuman Scan", "https://hanumanscan.com", "en")
