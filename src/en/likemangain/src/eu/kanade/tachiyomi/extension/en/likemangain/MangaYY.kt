package eu.kanade.tachiyomi.extension.en.likemangain

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaYY : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMMM, yyyy", Locale.US)
    override val chapterMode = ChapterMode.MangaAjax
}
