package eu.kanade.tachiyomi.extension.tr.ghosthentai

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class GhosToon : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
    override fun searchCardSelector() = archiveSelector()

    override fun parseChapterDate(date: String?) = 0L

    override val pageListParseSelector = "div.page-break img.wp-manga-chapter-img"
}
