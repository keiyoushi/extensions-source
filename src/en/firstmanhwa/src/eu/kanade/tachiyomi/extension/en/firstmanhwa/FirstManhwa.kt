package eu.kanade.tachiyomi.extension.en.firstmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FirstManhwa : Madara("1st Manhwa", "https://1stmanhwa.com", "en") {
    override val useNewChapterEndpoint = true
    override val filterNonMangaItems = false
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
