package eu.kanade.tachiyomi.extension.all.mangacrazy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaCrazy : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
}
