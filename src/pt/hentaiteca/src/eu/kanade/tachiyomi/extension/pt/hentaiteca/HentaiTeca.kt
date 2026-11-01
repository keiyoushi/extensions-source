package eu.kanade.tachiyomi.extension.pt.hentaiteca

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class HentaiTeca : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
}
