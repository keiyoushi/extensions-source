package eu.kanade.tachiyomi.extension.en.hentaixcomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiXComic : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US)
    override val chapterMode = ChapterMode.MangaAjax
}
