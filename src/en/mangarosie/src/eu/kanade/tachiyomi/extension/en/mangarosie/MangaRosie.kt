package eu.kanade.tachiyomi.extension.en.mangarosie

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaRosie : Madara("MangaRosie", "https://mangarosie.in", "en") {
    override val useNewChapterEndpoint = false
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
}
