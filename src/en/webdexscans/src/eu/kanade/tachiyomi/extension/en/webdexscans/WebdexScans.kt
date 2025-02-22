package eu.kanade.tachiyomi.extension.en.webdexscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class WebdexScans : Madara("Webdex Scans", "https://webdexscans.com", "en") {
    override val mangaSubString = "series"
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
}
