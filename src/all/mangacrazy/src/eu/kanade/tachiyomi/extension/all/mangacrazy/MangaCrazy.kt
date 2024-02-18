package eu.kanade.tachiyomi.extension.all.mangacrazy

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaCrazy : Madara("MangaCrazy", "https://mangacrazy.net", "all") {
    override val useNewChapterEndpoint = true
}
