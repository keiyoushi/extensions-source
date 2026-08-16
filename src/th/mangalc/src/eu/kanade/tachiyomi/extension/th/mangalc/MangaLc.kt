package eu.kanade.tachiyomi.extension.th.mangalc

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaLc : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("th"))
    override val pageListParseSelector = ".reading-content img"
    override val filterNonMangaItems = false
}
