package eu.kanade.tachiyomi.extension.en.darkscan

import eu.kanade.tachiyomi.multisrc.madara.Madara

class DarkScan : Madara("Dark-scan", "https://dark-scan.com", "en") {
    override val useNewChapterEndpoint = true
}
