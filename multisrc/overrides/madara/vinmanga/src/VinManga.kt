package eu.kanade.tachiyomi.extension.en.vinmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class VinManga : Madara("VinManga", "https://vinload.com", "en") {
    override val useNewChapterEndpoint = true
}
