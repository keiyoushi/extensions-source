package eu.kanade.tachiyomi.extension.en.mangaowlio

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaOwlIo : Madara("MangaOwl.io (unoriginal)", "https://mangaowl.io", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
