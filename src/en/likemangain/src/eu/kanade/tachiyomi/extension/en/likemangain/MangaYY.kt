package eu.kanade.tachiyomi.extension.en.likemangain

import eu.kanade.tachiyomi.multisrc.madara.Madara

class MangaYY : Madara("MangaYY", "https://mangayy.org", "en") {
    override val id = 828698548689586603
    override fun searchMangaSelector() = popularMangaSelector()
    override val useNewChapterEndpoint = true
}
