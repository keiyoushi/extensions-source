package eu.kanade.tachiyomi.extension.ar.manga3asq

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Manga3asq : Madara() {
    // \u060c (،) U+060C : ARABIC COMMA
    override val dateFormat = SimpleDateFormat("d MMM\u060c yyy", Locale("ar"))
    override val useNewChapterEndpoint: Boolean = true
    override val popularMangaUrlSelector = "div.post-title a:not([target])"
}
