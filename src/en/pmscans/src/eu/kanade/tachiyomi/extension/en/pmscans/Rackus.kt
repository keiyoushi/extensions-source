package eu.kanade.tachiyomi.extension.en.pmscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia

class Rackus : MangaThemesia(
    "Rackus",
    "https://rackusreads.com",
    "en",
) {
    override val versionId = 3
}
