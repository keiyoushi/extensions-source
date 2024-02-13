package eu.kanade.tachiyomi.extension.en.mangadinotop

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaDinoTop : Madara("MangaDino.top (unoriginal)", "https://mangadino.top", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
