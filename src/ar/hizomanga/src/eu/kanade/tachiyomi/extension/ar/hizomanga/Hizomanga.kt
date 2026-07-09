package eu.kanade.tachiyomi.extension.ar.hizomanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Hizomanga : Madara() {
    override val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override val mangaSubString = "serie"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val mangaDetailsSelectorDescription = ".manga-excerpt"
    override val mangaDetailsSelectorStatus = ".manga-status"
}
