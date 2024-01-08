package eu.kanade.tachiyomi.extension.en.webdexscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class WebdexScans : Madara("Webdex Scans", "https://webdexscans.com", "en") {
    override val useNewChapterEndpoint = true
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
