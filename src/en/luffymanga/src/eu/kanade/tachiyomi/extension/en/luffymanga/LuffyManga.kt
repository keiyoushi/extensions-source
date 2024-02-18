package eu.kanade.tachiyomi.extension.en.luffymanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class LuffyManga : Madara("Luffy Manga", "https://luffymanga.com", "en") {
    override val useNewChapterEndpoint = false
}
