package eu.kanade.tachiyomi.extension.en.manhwa68

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Manhwa68 : Madara() {
    override val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
