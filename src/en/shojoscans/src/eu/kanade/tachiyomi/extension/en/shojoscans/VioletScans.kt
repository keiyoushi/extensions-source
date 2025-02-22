package eu.kanade.tachiyomi.extension.en.shojoscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class VioletScans : MangaThemesia(
    "Violet Scans",
    "https://violetscans.com",
    "en",
    mangaUrlDirectory = "/comics",
) {
    override val id = 9079184529211162476
}
