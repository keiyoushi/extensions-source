package eu.kanade.tachiyomi.extension.ar.detectiveconanar

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DetectiveConanAr : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale("ar"))
    override val chapterMode = ChapterMode.MangaAjax
}
