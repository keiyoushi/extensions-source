package eu.kanade.tachiyomi.extension.en.zinmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Zinmanga : Madara("Zinmanga", "https://zinmanga.com", "en") {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
