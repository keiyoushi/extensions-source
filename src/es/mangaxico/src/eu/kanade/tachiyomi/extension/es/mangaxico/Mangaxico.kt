package eu.kanade.tachiyomi.extension.es.mangaxico

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Mangaxico : Madara(
    "Mangaxico",
    "https://mangaxico.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint = true
    override val chapterUrlSuffix = ""
}
