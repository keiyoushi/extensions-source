package eu.kanade.tachiyomi.extension.ar.mangarose

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaRose : Madara(
    "Manga Rose",
    "https://mangarose.net",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }
}
