package eu.kanade.tachiyomi.extension.tr.mangazure

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaZure : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale("tr"))
}
