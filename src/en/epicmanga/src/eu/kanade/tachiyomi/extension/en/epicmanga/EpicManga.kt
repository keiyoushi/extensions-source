package eu.kanade.tachiyomi.extension.en.epicmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class EpicManga : Madara("EpicManga", "https://epicmanga.co", "en") {
    override val useNewChapterEndpoint = true
}
