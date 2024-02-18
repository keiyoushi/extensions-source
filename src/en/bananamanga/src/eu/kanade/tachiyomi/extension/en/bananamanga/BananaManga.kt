package eu.kanade.tachiyomi.extension.en.bananamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class BananaManga : Madara("Banana Manga", "https://bananamanga.net", "en") {
    override val useNewChapterEndpoint = true
}
