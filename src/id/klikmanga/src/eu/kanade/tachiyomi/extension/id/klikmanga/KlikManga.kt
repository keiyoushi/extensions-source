package eu.kanade.tachiyomi.extension.id.klikmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class KlikManga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("id"))
    override val mangaSubString = "daftar-komik"
    override val chapterMode = ChapterMode.MangaAjax
}
