package eu.kanade.tachiyomi.extension.en.mangadrama

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.Madara.LoadMoreStrategy
import keiyoushi.annotation.Source

@Source
abstract class MangaDrama : Madara() {

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
    override fun chapterListSelector() = "li.wp-manga-chapter.free-chap"

    override fun searchMangaSelector() = popularMangaSelector()
}
