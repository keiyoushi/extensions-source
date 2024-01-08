package eu.kanade.tachiyomi.extension.en.mangaowlone

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaOwlOne : Madara("MangaOwl.one (unoriginal)", "https://mangaowl.one", "en") {
    override val useNewChapterEndpoint = false
    override val filterNonMangaItems = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
