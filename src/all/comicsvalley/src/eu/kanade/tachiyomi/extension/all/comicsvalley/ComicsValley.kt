package eu.kanade.tachiyomi.extension.all.comicsvalley

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class ComicsValley : Madara() {
    override val mangaSubString = "comics-new"
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
