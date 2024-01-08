package eu.kanade.tachiyomi.extension.en.mangarawinfo

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaRawInfo : Madara("Manga-Raw.info (unoriginal)", "https://manga-raw.info", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
