package eu.kanade.tachiyomi.extension.ar.gatemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Gatemanga : Madara(
    "Gatemanga",
    "https://gatemanga.com",
    "ar",
    dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar")),
) {
    override val mangaSubString = "ar"
}
