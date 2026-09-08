package eu.kanade.tachiyomi.extension.ar.manga3asq

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Manga3asq : MadaraNoAjax() {
    // \u060c (،) U+060C : ARABIC COMMA
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMM\u060c yyy", Locale("ar"))
    override val chapterDateSelector = "span.chapter-release-date .timediff"
    override val chapterMode = ChapterMode.MangaAjax
    override val archiveUrlSelector = "div.post-title a:not([target])"
}
