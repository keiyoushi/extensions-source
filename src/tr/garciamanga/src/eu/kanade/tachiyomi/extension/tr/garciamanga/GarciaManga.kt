package eu.kanade.tachiyomi.extension.tr.garciamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class GarciaManga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("tr"))
    override val chapterMode = ChapterMode.MangaAjax

    override val filterNonMangaItems = false
}
