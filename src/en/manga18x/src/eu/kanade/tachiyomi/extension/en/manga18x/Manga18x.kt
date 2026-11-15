package eu.kanade.tachiyomi.extension.en.manga18x

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class Manga18x : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
