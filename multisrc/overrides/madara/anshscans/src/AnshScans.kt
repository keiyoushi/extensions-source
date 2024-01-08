package eu.kanade.tachiyomi.extension.en.anshscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class AnshScans : Madara("Ansh Scans", "https://anshscans.org", "en") {
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override val mangaSubString = "comic"
}
