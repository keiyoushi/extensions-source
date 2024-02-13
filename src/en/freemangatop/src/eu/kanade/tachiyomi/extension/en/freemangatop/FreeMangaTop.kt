package eu.kanade.tachiyomi.extension.en.freemangatop

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FreeMangaTop : Madara("FreeMangaTop", "https://freemangatop.com", "en") {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
