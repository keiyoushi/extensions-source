package eu.kanade.tachiyomi.extension.ar.mangasoul
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class MangaSoul : ZeistManga("Manga Soul", "https://www.manga-soul.com", "ar") {
    override val mangaDetailsSelectorInfo = ".y6x11p, .y6x11p > b > span"
}
