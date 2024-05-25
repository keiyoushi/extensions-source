package eu.kanade.tachiyomi.extension.pt.dreamscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class DreamScan : Madara(
    "Dream Scan",
    "https://dreamscan.com.br",
    "pt-br",
    SimpleDateFormat("MMMM d, yyyy", Locale("pt", "br")),
) {
    override val useNewChapterEndpoint = true
}
