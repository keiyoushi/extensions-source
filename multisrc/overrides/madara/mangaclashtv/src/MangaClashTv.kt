package eu.kanade.tachiyomi.extension.en.mangaclashtv

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaClashTv : Madara("MangaClash.tv (unoriginal)", "https://mangaclash.tv", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
