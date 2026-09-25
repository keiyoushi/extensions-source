package eu.kanade.tachiyomi.extension.pt.fenixproject

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class FenixProject : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    override val chapterMode = ChapterMode.MangaAjax

    override suspend fun fetchChapterDocument(chapterUrl: String) = super.fetchChapterDocument("$chapterUrl?nocache=${System.currentTimeMillis()}")
}
