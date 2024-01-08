package eu.kanade.tachiyomi.extension.en.topmanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TopManhua : Madara("Top Manhua", "https://topmanhua.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US)) {
    // The website does not flag the content.
    override val filterNonMangaItems = false
}
