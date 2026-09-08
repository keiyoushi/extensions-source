package eu.kanade.tachiyomi.extension.tr.anikiga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Anikiga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("tr"))
    override val chapterMode = ChapterMode.MangaAjax
}
