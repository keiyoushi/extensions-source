package eu.kanade.tachiyomi.extension.en.toonizy

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class Toonizy : Madara() {
    override val dateFormat = SimpleDateFormat("MMM d, yy", Locale.ENGLISH)
    override val useNewChapterEndpoint = true
}
