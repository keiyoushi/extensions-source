package eu.kanade.tachiyomi.extension.tr.mangabari

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaBari : Madara(
    "Manga Bari",
    "https://mangabari.me",
    "tr",
    dateFormat = java.text.SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val useNewChapterEndpoint = true
}
