package eu.kanade.tachiyomi.extension.ar.paradisebl

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ParadiseBL : Madara() {
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaDetailsSelectorTitle = ".new-post-title h3 a"
}
