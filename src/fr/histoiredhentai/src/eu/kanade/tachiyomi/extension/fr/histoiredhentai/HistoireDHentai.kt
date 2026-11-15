package eu.kanade.tachiyomi.extension.fr.histoiredhentai

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HistoireDHentai : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.FRENCH)
}
