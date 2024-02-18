package eu.kanade.tachiyomi.extension.en.pawmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class PawManga : Madara("Paw Manga", "https://pawmanga.com", "en") {
    override val useNewChapterEndpoint = true
}
