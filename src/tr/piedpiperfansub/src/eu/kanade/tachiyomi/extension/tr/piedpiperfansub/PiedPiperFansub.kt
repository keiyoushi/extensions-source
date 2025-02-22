package eu.kanade.tachiyomi.extension.tr.piedpiperfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class PiedPiperFansub : Madara(
    "Pied Piper Fansub",
    "https://piedpiperfansub.me",
    "tr",
    dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val useNewChapterEndpoint = true
}
