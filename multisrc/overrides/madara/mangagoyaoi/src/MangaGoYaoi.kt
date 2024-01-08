package eu.kanade.tachiyomi.extension.en.mangagoyaoi

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaGoYaoi : Madara("MangaGo Yaoi", "https://mangagoyaoi.com", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
