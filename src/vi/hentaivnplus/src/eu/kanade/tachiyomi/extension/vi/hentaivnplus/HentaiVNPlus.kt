package eu.kanade.tachiyomi.extension.vi.hentaivnplus

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HentaiVNPlus : Madara() {
    override val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
    override val mangaSubString = "truyen-hentai"
    override val pageListParseSelector = ".reading-content img"
}
