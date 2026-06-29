package eu.kanade.tachiyomi.extension.en.mangadia

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaDia : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("tr"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
