package eu.kanade.tachiyomi.extension.en.zinmangatop

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ZinMangaTop : Madara("ZinManga.top (unoriginal)", "https://zinmanga.top", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
