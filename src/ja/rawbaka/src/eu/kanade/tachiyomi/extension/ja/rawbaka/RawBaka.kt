package eu.kanade.tachiyomi.extension.ja.rawbaka

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class RawBaka : Madara() {
    override val filterNonMangaItems = false

    override val chapterMode = ChapterMode.MangaAjax
}
