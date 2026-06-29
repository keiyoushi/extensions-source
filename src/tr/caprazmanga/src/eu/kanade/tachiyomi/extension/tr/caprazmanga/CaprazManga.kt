package eu.kanade.tachiyomi.extension.tr.caprazmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara

class CaprazManga : Madara("ÇaprazManga", "https://caprazmanga.com", "tr") {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
