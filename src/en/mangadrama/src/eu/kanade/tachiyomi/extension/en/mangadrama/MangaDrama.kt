package eu.kanade.tachiyomi.extension.en.mangadrama

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.Madara.LoadMoreStrategy

class MangaDrama : Madara("Manga Drama", "https://mangadrama.com", "en") {

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
    override fun chapterListSelector() = "li.wp-manga-chapter.free-chap"

    override fun searchMangaSelector() = popularMangaSelector()
}
