package eu.kanade.tachiyomi.extension.ar.mangatuk

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaTuk : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar"))
    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
