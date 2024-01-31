package eu.kanade.tachiyomi.extension.en.elitemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class EliteManga : Madara("Elite Babes", "https://www.elitebabes.com", "en") {
    override val useNewChapterEndpoint = true
    override val filterNonMangaItems = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
