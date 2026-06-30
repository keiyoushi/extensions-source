package eu.kanade.tachiyomi.extension.en.hentaisco

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source

@Source
abstract class HentaiSco : Madara() {
    override val mangaSubString = "hentai"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
