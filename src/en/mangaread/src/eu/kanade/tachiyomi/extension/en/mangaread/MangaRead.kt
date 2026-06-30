package eu.kanade.tachiyomi.extension.en.mangaread

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaRead : Madara() {
    override val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
}
