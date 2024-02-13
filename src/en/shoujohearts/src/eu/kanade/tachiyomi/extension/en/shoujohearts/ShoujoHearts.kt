package eu.kanade.tachiyomi.extension.en.shoujohearts

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ShoujoHearts : Madara("ShoujoHearts", "https://shoujohearts.com", "en") {

    override val mangaSubString = "reader/manga"

    override fun searchPage(page: Int): String = "reader/page/$page/"
}
