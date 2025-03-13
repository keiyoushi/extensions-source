package eu.kanade.tachiyomi.extension.en.murimscan

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MurimScan : Madara("MurimScan", "https://inkreads.com", "en") {
    override val useNewChapterEndpoint = false
    override val mangaSubString = "mangax"
}
