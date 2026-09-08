package eu.kanade.tachiyomi.extension.en.manhuaus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhuaUS : Madara() {
    override val filterNonMangaItems = false
    override val chapterMode = ChapterMode.MangaAjax
}
