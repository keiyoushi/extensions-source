package eu.kanade.tachiyomi.extension.en.toonizy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Toonizy : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, yy", Locale.ENGLISH)
    override val chapterMode = ChapterMode.MangaAjax

    override val mangaSubString = "webtoon"

    override val mangaDetailsSelectorTitle = ".post-content h3"
}
