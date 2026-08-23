package eu.kanade.tachiyomi.extension.es.marmota

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Marmota : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale("es"))
    override val mangaSubString: String = "comic"
    override val chapterMode = ChapterMode.MangaAjax
}
