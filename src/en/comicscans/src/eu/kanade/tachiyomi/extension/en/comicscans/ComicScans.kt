package eu.kanade.tachiyomi.extension.en.comicscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ComicScans : Madara("Comic Scans", "https://www.comicscans.org", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
