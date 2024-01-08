package eu.kanade.tachiyomi.extension.en.mangakakalotone

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangakakalotOne : Madara("Mangakakalot.one (unoriginal)", "https://mangakakalot.one", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
