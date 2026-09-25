package eu.kanade.tachiyomi.extension.ar.mangaspark

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaSpark : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM، yyyy", Locale.forLanguageTag("ar"))
    override val chapterMode = ChapterMode.AdminAjax
    override val pageListParseSelector = "div.wp-manga-chapter-img img, div.reading-content img"
}
