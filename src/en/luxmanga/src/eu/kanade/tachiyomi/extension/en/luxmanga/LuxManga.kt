package eu.kanade.tachiyomi.extension.en.luxmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class LuxManga : Madara("LuxManga", "https://luxmanga.net", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
