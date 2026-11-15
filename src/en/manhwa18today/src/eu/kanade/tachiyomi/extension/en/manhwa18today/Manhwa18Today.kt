package eu.kanade.tachiyomi.extension.en.manhwa18today

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Manhwa18Today : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
