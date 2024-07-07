package eu.kanade.tachiyomi.extension.en.darkscancom

import eu.kanade.tachiyomi.multisrc.madara.Madara

class DarkScanCom : Madara(
    "Dark-Scan.com",
    "https://dark-scan.com",
    "en",
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
}
