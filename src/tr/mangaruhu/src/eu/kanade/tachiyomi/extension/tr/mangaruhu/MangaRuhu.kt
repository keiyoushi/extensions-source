package eu.kanade.tachiyomi.extension.tr.mangaruhu

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaRuhu : Madara(
    "Manga Ruhu",
    "https://mangaruhu.com",
    "tr",
    SimpleDateFormat("d MMMM yyyy", Locale("tr")),
) {
    override val filterNonMangaItems = false
}
