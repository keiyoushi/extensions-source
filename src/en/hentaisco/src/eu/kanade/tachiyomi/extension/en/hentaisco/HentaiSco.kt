package eu.kanade.tachiyomi.extension.en.hentaisco

import eu.kanade.tachiyomi.multisrc.madara.Madara

class HentaiSco : Madara("HentaiSco", "https://hentaisco.cc", "en") {
    override val mangaSubString = "hentai"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
