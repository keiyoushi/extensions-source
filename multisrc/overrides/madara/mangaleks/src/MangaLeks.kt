package eu.kanade.tachiyomi.extension.ar.mangaleks

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLeks : Madara(
    "مانجا ليكس",
    "https://mangaleks.com",
    "ar",
    SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH),
) {
    override fun searchPage(page: Int): String {
        return if (page > 1) {
            "page/$page/"
        } else {
            ""
        }
    }
}
