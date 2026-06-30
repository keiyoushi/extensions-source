package eu.kanade.tachiyomi.extension.en.shibamanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ShibaManga : Madara() {
    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    override val filterNonMangaItems = false
    override val useNewChapterEndpoint = true
}
