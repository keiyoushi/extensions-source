package eu.kanade.tachiyomi.extension.en.archerscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ArcheRScans : Madara("ArcheR Scans", "https://www.archerscans.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
