package eu.kanade.tachiyomi.extension.en.mangadrama

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.Madara.LoadMoreStrategy

class MangaDrama : Madara("Manga Drama", "https://mangadrama.com", "en") {

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun searchMangaSelector() = "div.page-item-detail:not(:has(a[href*='bilibilicomics.com']))$mangaEntrySelector"

    override val searchMangaUrlSelector = "div.post-title a"
}
