package eu.kanade.tachiyomi.extension.ar.mangahub

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class MangaHub : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
    override val supportsLatest = false

    override fun archiveSelector() = ".page-item-detail"

    override suspend fun getPopularManga(page: Int) = archivePage(page, "", "/")
}
