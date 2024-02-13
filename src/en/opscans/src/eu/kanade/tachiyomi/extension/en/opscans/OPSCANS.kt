package eu.kanade.tachiyomi.extension.en.opscans

import eu.kanade.tachiyomi.multisrc.madara.Madara

class OPSCANS : Madara("OPSCANS", "https://opchapters.com", "en") {
    override val versionId = 2

    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }
}
