package eu.kanade.tachiyomi.extension.es.ragnarokscanlation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class RagnarokScanlation : Madara() {
    override val mangaSubString = "series"
    override val chapterMode = ChapterMode.MangaAjax
}
