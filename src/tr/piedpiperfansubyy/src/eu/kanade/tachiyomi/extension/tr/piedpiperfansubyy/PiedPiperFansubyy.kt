package eu.kanade.tachiyomi.extension.tr.piedpiperfansubyy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class PiedPiperFansubyy : Madara(
    "Pied Piper Fansubyy",
    "https://piedpiperfansubyy.me",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val useNewChapterEndpoint = true
}
