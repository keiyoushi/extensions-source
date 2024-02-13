package eu.kanade.tachiyomi.extension.en.ponymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class PonyManga : Madara("Pony Manga", "https://ponymanga.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
