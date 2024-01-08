package eu.kanade.tachiyomi.extension.en.factmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FactManga : Madara("FactManga", "https://factmanga.com", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
