package eu.kanade.tachiyomi.extension.en.nitroscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class NitroScans : Madara("Nitro Manga", "https://nitromanga.com", "en") {
    override val id = 1310352166897986481

    override val mangaSubString = "mangas"

    override val filterNonMangaItems = false

    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
