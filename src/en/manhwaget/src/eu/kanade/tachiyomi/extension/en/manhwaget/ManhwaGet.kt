package eu.kanade.tachiyomi.extension.en.manhwaget

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source

@Source
abstract class ManhwaGet : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
