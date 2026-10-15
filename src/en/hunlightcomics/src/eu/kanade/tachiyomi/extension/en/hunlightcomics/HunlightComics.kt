package eu.kanade.tachiyomi.extension.en.hunlightcomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class HunlightComics : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
