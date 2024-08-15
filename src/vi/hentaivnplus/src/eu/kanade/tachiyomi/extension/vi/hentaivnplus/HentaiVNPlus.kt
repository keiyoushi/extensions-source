package eu.kanade.tachiyomi.extension.vi.hentaivnplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiVNPlus : Madara(
    "HentaiVN.plus",
    "https://hentaivn.cafe",
    "vi",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "truyen-hentai"
}
