package eu.kanade.tachiyomi.extension.en.mangacultivator

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaCultivator : Madara("MangaCultivator", "https://mangacultivator.com", "en") {
    override val useNewChapterEndpoint: Boolean = true
}
