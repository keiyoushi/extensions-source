package eu.kanade.tachiyomi.extension.vi.truyenvn

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TruyenVN : Madara(
    "TruyenVN",
    "https://truyenvn.wiki",
    "vi",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaSubString = "truyen-tranh"
}
