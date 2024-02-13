package eu.kanade.tachiyomi.extension.es.herenscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HerenScan : Madara(
    "HerenScan",
    "https://herenscan.com",
    "es",
    dateFormat = SimpleDateFormat("d 'de' MMM 'de' yyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true

    // Disable type selector as it's junk data, must not be empty.
    override val seriesTypeSelector = "#abcdefghijklmnopqrstuvwxyz"
}
