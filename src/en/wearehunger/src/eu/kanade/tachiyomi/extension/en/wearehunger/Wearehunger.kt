package eu.kanade.tachiyomi.extension.en.wearehunger

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class Wearehunger : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
