package eu.kanade.tachiyomi.extension.en.likemangain

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import keiyoushi.annotation.Source

@Source
abstract class MangaYY : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.US)
    override fun searchMangaSelector() = popularMangaSelector()
    override val useNewChapterEndpoint = true
}
