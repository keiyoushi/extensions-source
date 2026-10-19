package eu.kanade.tachiyomi.extension.en.lovesjanescans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class LovesjaneScans : Madara() {
    override val chapterMode = ChapterMode.MangaAjaxPaginated
}
