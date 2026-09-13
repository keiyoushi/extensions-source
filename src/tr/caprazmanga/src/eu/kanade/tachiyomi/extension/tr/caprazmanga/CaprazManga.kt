package eu.kanade.tachiyomi.extension.tr.caprazmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class CaprazManga : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
