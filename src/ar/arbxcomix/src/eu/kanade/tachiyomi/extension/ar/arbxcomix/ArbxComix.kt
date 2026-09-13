package eu.kanade.tachiyomi.extension.ar.arbxcomix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ArbxComix : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
