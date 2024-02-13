package eu.kanade.tachiyomi.extension.en.mangatyrant

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaTyrant : Madara("MangaTyrant", "https://mangatyrant.com", "en") {
    override val useNewChapterEndpoint = true
    override val filterNonMangaItems = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
