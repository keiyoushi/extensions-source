package eu.kanade.tachiyomi.extension.tr.mindafansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MindaFansub : Madara() {
    override val seriesTypeSelector = ".summary-heading:has(h5:containsOwn(Tür)) + .summary-content"
    override val altNameSelector = ".summary-heading:has(h5:containsOwn(Diğer Adları)) + .summary-content"
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.forLanguageTag("tr"))
}
