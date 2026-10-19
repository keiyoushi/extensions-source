package eu.kanade.tachiyomi.extension.ar.nijitranslations

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NijiTranslations : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale("ar"))
    override val chapterMode = ChapterMode.AdminAjax
}
