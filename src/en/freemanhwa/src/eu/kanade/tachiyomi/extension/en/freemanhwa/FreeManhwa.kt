package eu.kanade.tachiyomi.extension.en.freemanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara

class FreeManhwa : Madara("Free Manhwa", "https://manhwas.com", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
