package eu.kanade.tachiyomi.extension.en.mangakakalotio

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangakakalotIo : Madara("Mangakakalot.io (unoriginal)", "https://mangakakalot.io", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
