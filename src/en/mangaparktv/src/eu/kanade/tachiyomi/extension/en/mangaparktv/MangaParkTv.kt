package eu.kanade.tachiyomi.extension.en.mangaparktv

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class MangaParkTv : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
