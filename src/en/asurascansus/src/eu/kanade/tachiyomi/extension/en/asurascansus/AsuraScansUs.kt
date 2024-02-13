package eu.kanade.tachiyomi.extension.en.asurascansus

import eu.kanade.tachiyomi.multisrc.madara.Madara

class AsuraScansUs : Madara("Asura Scans.us (unoriginal)", "https://asurascans.us", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
