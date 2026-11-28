package eu.kanade.tachiyomi.extension.tr.kuroimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class KuroiManga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
    override val chapterMode = ChapterMode.MangaAjax

    override val chapterUrlSelector = "a:not(:has(img))"
}
