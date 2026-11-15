package eu.kanade.tachiyomi.extension.th.doujinxh

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class DoujinxH : Madara() {
    override val mangaSubString = "doujin"
    override val genreDirectory = "doujin-genre"
    override val chapterMode = ChapterMode.MangaAjax
}
