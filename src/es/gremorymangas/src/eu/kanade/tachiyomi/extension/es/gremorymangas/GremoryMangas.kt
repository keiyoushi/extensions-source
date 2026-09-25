package eu.kanade.tachiyomi.extension.es.gremorymangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class GremoryMangas : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))
    override val chapterMode = ChapterMode.MangaAjax
}
