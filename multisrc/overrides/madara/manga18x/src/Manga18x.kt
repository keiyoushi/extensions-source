package eu.kanade.tachiyomi.extension.en.manga18x

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Manga18x : Madara("Manga 18x", "https://manga18x.net", "en") {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
