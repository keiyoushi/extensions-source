package eu.kanade.tachiyomi.extension.en.murimscan

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MurimScan : Madara("MurimScan", "https://murimscan.run", "en") {
    override val useNewChapterEndpoint = false
}
