package eu.kanade.tachiyomi.extension.en.bananamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BananaManga : Madara("Banana Manga", "https://bananamanga.net", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
