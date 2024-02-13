package eu.kanade.tachiyomi.extension.en.mangatxgg

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaTxGg : Madara("Manga Tx.gg (unoriginal)", "https://mangatx.gg", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
