package eu.kanade.tachiyomi.extension.en.gakamangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class GakaMangas : Madara() {
    override val filterNonMangaItems = false
    override val chapterMode = ChapterMode.MangaAjax
}
