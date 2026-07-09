package eu.kanade.tachiyomi.extension.th.doujinlc

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DoujinLc : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th"))
    override val pageListParseSelector = ".reading-content img"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val mangaSubString = "doujin"
    override val filterNonMangaItems = false
}
