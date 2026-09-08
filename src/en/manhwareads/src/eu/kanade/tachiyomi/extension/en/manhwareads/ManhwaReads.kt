package eu.kanade.tachiyomi.extension.en.manhwareads

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class ManhwaReads : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
