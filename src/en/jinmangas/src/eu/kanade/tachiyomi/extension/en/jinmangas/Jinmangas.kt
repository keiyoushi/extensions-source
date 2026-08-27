package eu.kanade.tachiyomi.extension.en.jinmangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Jinmangas : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
