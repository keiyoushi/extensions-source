package eu.kanade.tachiyomi.extension.id.birdtoon

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BirdToon : Madara("BirdToon", "https://birdtoon.net", "id") {
    override val mangaSubString = "komik"

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }
}
