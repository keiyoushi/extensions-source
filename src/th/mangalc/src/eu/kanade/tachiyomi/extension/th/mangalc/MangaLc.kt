package eu.kanade.tachiyomi.extension.th.mangalc

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaLc : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("th"))
    override val pageListParseSelector = ".reading-content img"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val filterNonMangaItems = false
}
