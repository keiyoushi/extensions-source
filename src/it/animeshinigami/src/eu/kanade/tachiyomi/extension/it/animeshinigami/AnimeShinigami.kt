package eu.kanade.tachiyomi.extension.it.animeshinigami

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class AnimeShinigami : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
