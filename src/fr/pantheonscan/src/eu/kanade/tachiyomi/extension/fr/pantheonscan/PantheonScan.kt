package eu.kanade.tachiyomi.extension.fr.pantheonscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class PantheonScan : Madara() {
    override val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
    override val useNewChapterEndpoint = true
}
