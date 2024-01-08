package eu.kanade.tachiyomi.extension.en.luffymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class LuffyManga : Madara("Luffy Manga", "https://luffymanga.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
