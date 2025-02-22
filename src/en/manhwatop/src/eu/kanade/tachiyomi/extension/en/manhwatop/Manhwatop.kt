package eu.kanade.tachiyomi.extension.en.manhwatop

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manhwatop : Madara("Manhwatop", "https://manhwatop.com", "en") {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
