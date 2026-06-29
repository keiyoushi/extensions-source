package eu.kanade.tachiyomi.extension.pt.dreamscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class DreamScan : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("pt", "BR"))
    override val id: Long = 2058412298484770949

    override val useNewChapterEndpoint = true
}
