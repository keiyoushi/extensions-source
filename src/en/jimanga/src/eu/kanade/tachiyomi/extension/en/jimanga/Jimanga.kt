package eu.kanade.tachiyomi.extension.en.jimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Jimanga : Madara("Jimanga", "https://jimanga.com", "en") {
    override val useNewChapterEndpoint = false
    override val filterNonMangaItems = false
}
