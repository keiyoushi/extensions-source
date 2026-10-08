package eu.kanade.tachiyomi.extension.ar.arhentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ArHentai : Madara() {
    override val mangaSubString = "title"
    override val chapterMode = ChapterMode.MangaAjax
}
