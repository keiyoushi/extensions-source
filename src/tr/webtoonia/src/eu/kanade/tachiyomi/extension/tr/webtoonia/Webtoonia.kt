package eu.kanade.tachiyomi.extension.tr.webtoonia

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class Webtoonia : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
