package eu.kanade.tachiyomi.extension.ko.manytoonclub

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManyToonClub : Madara("ManyToonClub", "https://manytoon.club", "ko") {

    override val mangaSubString = "manhwa-raw"

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
