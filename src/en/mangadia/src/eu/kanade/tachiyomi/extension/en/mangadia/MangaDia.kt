package eu.kanade.tachiyomi.extension.en.mangadia

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaDia : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("tr"))
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
