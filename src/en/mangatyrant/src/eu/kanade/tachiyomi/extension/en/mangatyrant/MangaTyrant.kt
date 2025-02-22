package eu.kanade.tachiyomi.extension.en.mangatyrant

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaTyrant : Madara("MangaTyrant", "https://mangatyrant.com", "en") {
    override val useNewChapterEndpoint = true
    override val filterNonMangaItems = false
}
