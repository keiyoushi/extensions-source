package eu.kanade.tachiyomi.extension.en.manga18h

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manga18h : Madara("Manga 18h", "https://manga18h.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
