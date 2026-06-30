package eu.kanade.tachiyomi.extension.pt.dreamscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DreamScan : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("pt", "BR"))
    override val useNewChapterEndpoint = true
}
