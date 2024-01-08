package eu.kanade.tachiyomi.extension.en.mangaqueenonline

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaQueenOnline : Madara("Manga Queen.online (unoriginal)", "https://mangaqueen.online", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
