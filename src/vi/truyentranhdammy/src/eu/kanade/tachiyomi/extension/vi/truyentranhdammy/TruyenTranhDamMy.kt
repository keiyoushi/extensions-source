package eu.kanade.tachiyomi.extension.vi.truyentranhdammy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TruyenTranhDamMy :
    Madara(
        "Truyện tranh đam mỹ",
        "https://truyentranhdammyy.site",
        "vi",
        SimpleDateFormat("MMMM d, yyyy", Locale("vi")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
