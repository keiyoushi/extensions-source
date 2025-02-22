package eu.kanade.tachiyomi.extension.en.mangatxunoriginal
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaEmpress : Madara(
    "Manga Empress",
    "https://mangaempress.com",
    "en",
) {
    // formally Manga-TX
    override val id = 3683271326486389724
}
