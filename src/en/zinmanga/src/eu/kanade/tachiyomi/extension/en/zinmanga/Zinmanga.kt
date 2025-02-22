package eu.kanade.tachiyomi.extension.en.zinmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Zinmanga : Madara("Zinmanga", "https://mangazin.org", "en") {

    // The website does not flag the content consistently.
    override val filterNonMangaItems = false
}
