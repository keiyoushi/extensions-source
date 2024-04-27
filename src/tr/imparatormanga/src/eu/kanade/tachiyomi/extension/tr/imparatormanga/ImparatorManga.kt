package eu.kanade.tachiyomi.extension.tr.imparatormanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ImparatorManga : Madara(
    "İmparator Manga",
    "https://www.imparatormanga.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override val useNewChapterEndpoint = true
}
