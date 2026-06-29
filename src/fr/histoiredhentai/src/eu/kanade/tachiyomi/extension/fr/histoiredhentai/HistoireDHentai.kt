package eu.kanade.tachiyomi.extension.fr.histoiredhentai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class HistoireDHentai : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)
}
