package eu.kanade.tachiyomi.extension.en.topmanhuanet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class TopManhuaNet : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
