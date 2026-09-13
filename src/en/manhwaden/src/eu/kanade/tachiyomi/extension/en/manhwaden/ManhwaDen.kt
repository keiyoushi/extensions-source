package eu.kanade.tachiyomi.extension.en.manhwaden

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

@Source
abstract class ManhwaDen : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax
    override val filterNonMangaItems = false
}
