package eu.kanade.tachiyomi.extension.en.mangak2

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaK2 : Madara("MangaK2", "https://mangak2.com", "en") {
    override val useNewChapterEndpoint = true
}
