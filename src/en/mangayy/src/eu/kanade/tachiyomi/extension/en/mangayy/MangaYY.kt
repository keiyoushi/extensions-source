package eu.kanade.tachiyomi.extension.en.mangayy

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaYY : Madara("MangaYY", "https://mangayy.org", "en") {
    override fun searchMangaSelector() = popularMangaSelector()
    override val useNewChapterEndpoint = true
}
