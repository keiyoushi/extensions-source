package eu.kanade.tachiyomi.extension.en.shibamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class ShibaManga : Madara() {
    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    override val filterNonMangaItems = false
    override val useNewChapterEndpoint = true
}
