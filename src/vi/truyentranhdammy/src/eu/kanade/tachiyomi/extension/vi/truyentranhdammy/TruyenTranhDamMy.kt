package eu.kanade.tachiyomi.extension.vi.truyentranhdammy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class TruyenTranhDamMy : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("vi"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
