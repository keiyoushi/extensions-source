package eu.kanade.tachiyomi.extension.en.hentaixcomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class HentaiXComic : Madara() {
    override val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
}
