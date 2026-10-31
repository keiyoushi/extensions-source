package eu.kanade.tachiyomi.extension.tr.tarotscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class TarotScans : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
