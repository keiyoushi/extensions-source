package eu.kanade.tachiyomi.extension.en.mangareadorg

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaReadOrg : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyy", Locale.US)

    // Switch to the new JS-based AJAX endpoint because
    // the raw HTML doesn't contain the chapters initially
    override val chapterMode = ChapterMode.MangaAjax
}
