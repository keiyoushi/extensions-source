package eu.kanade.tachiyomi.extension.en.manganerds

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaNerds : Madara("Manga Nerds", "https://manganerds.com", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
