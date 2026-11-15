package eu.kanade.tachiyomi.extension.en.mangafree

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Mangafree : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
