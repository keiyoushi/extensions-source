package eu.kanade.tachiyomi.extension.ar.mangastarz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaStarz : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM، yyyy", Locale.forLanguageTag("ar"))
}
