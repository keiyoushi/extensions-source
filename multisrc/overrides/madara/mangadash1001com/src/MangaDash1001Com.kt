package eu.kanade.tachiyomi.extension.en.mangadash1001com

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaDash1001Com : Madara("Manga-1001.com", "https://manga-1001.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
