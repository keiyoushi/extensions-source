package eu.kanade.tachiyomi.extension.ar.arbxcomix

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ArbxComix : Madara("ArbxComix", "https://arbxcomix.com", "ar") {
    override fun searchMangaSelector() = popularMangaSelector()
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
