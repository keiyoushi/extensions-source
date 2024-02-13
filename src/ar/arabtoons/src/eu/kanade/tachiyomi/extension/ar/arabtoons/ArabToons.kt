package eu.kanade.tachiyomi.extension.ar.arabtoons

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ArabToons : Madara(
    "عرب تونز",
    "https://arabtoons.net",
    "ar",
    dateFormat = SimpleDateFormat("MMM d", Locale("ar")),
) {
    override val useNewChapterEndpoint = true
}
