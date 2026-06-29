package eu.kanade.tachiyomi.extension.ar.hizomanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Hizomanga : Madara() {
    override val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override val mangaSubString = "serie"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val mangaDetailsSelectorDescription = ".manga-excerpt"
    override val mangaDetailsSelectorStatus = ".manga-status"
}
