package eu.kanade.tachiyomi.extension.en.mangaqueencom

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaQueenCom : Madara("Manga Queen.com", "https://mangaqueen.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
