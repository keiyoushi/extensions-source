package eu.kanade.tachiyomi.extension.pt.arcticscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ArcticScan : Madara(
    "Arctic Scan",
    "https://arcticscan.top",
    "pt-BR",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true
}
