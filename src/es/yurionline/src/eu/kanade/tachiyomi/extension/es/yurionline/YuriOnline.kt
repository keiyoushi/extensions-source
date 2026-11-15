package eu.kanade.tachiyomi.extension.es.yurionline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class YuriOnline : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale("es"))
    override val chapterMode = ChapterMode.MangaAjax
}
