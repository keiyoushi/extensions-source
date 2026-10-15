package eu.kanade.tachiyomi.extension.en.dailymanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class DailyManhwa : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
