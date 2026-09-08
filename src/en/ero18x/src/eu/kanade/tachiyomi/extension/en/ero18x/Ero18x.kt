package eu.kanade.tachiyomi.extension.en.ero18x

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class Ero18x : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
}
