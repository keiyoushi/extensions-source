package eu.kanade.tachiyomi.extension.en.readmangafree

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ReadMangaFree : Madara("ReadMangaFree", "https://readmangafree.net", "en") {
    override val useNewChapterEndpoint = false
}
