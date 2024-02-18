package eu.kanade.tachiyomi.extension.en.luxmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class LuxManga : Madara("LuxManga", "https://luxmanga.net", "en") {
    override val useNewChapterEndpoint = false
}
