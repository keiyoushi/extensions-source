package eu.kanade.tachiyomi.extension.ko.manytoonclub
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManyToonClub : Madara("ManyToonClub", "https://manytoon.club", "ko") {

    override val mangaSubString = "manhwa-raw"

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
