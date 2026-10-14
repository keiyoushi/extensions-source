package eu.kanade.tachiyomi.extension.ar.hizomanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Hizomanga : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    override val mangaSubString = "serie"

    override val mangaDetailsSelectorTitle = "div.manga-title > h2"
    override val mangaDetailsSelectorDescription = ".manga-excerpt .excerpt-content:not(:empty)"
    override val mangaDetailsSelectorStatus = ".manga-status"
}
