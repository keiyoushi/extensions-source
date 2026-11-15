package eu.kanade.tachiyomi.extension.th.doujinlc

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DoujinLc : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale("th"))
    override val pageListParseSelector = ".reading-content img"

    override val mangaSubString = "doujin"
    override val filterNonMangaItems = false
}
