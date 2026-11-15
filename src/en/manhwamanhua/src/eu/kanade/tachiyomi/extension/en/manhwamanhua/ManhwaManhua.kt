package eu.kanade.tachiyomi.extension.en.manhwamanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ManhwaManhua : Madara() {
    override val filterNonMangaItems = false
    override val chapterMode = ChapterMode.MangaAjax
}
