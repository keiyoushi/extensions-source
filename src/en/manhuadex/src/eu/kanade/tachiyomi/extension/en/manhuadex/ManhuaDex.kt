package eu.kanade.tachiyomi.extension.en.manhuadex

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManhuaDex : Madara("ManhuaDex", "https://manhuadex.com", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
