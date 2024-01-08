package eu.kanade.tachiyomi.extension.en.rio2manga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Rio2Manga : Madara("Rio2 Manga", "https://rio2manga.com", "en") {
    override val useNewChapterEndpoint = true
}
