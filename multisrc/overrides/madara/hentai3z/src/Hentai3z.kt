package eu.kanade.tachiyomi.extension.en.hentai3z

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Hentai3z : Madara("Hentai3z", "https://hentai3z.xyz", "en") {
    override val useNewChapterEndpoint = false

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
