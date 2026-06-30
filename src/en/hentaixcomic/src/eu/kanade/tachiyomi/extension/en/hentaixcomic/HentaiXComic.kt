package eu.kanade.tachiyomi.extension.en.hentaixcomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class HentaiXComic : Madara() {
    override val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
}
