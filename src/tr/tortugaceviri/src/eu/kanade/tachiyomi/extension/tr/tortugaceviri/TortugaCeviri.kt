package eu.kanade.tachiyomi.extension.tr.tortugaceviri

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TortugaCeviri : Madara(
    "Tortuga Ceviri",
    "https://tortuga-ceviri.com",
    "tr",
    SimpleDateFormat("MMM d, yyy", Locale("tr")),
) {
    override val versionId = 2

    override val useNewChapterEndpoint = true
}
