package eu.kanade.tachiyomi.extension.en.manhwanex

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class ManhwaNex : Madara() {
    override val chapterMode = ChapterMode.MangaAjax

    override val statusFilterOptions = super.statusFilterOptions + ("Upcoming" to "upcoming")
}
