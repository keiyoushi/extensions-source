package eu.kanade.tachiyomi.extension.en.hm2d

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class HM2D : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
