package eu.kanade.tachiyomi.extension.en.allporncomicio

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class AllPornComicIo : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    override val chapterMode = ChapterMode.MangaAjax
}
