package eu.kanade.tachiyomi.extension.en.manhwa68

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.multisrc.madara.MadaraBase.ChapterMode
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Manhwa68 : Madara() {
    override val chapterMode = ChapterMode.MangaAjax
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
