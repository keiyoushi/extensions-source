package eu.kanade.tachiyomi.extension.all.mangacrazy

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaCrazy : Madara("MangaCrazy", "https://mangacrazy.net", "all") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
