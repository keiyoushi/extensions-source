package eu.kanade.tachiyomi.extension.en.mangaupdatestop

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaUpdatesTop : Madara("MangaUpdates.top (unoriginal)", "https://mangaupdates.top", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
