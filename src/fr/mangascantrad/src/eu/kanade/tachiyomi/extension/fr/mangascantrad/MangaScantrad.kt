package eu.kanade.tachiyomi.extension.fr.mangascantrad

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaScantrad : Madara() {
    override val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)
    override val useNewChapterEndpoint: Boolean = true
}
