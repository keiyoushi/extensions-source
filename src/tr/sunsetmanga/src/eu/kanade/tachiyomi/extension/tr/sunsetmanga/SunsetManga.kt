package eu.kanade.tachiyomi.extension.tr.sunsetmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class SunsetManga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
}
