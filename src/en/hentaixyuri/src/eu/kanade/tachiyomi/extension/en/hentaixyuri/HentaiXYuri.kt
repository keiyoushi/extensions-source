package eu.kanade.tachiyomi.extension.en.hentaixyuri

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiXYuri : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US)
    override val chapterMode = ChapterMode.MangaAjax
}
