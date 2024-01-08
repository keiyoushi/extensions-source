package eu.kanade.tachiyomi.extension.en.treemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TreeManga : Madara(
    "TreeManga",
    "https://treemanga.com",
    "en",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US),
) {
    override val useNewChapterEndpoint: Boolean = true
}
