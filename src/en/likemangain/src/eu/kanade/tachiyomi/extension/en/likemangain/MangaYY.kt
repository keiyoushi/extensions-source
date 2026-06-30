package eu.kanade.tachiyomi.extension.en.likemangain

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaYY : Madara() {
    override val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.US)
    override fun searchMangaSelector() = popularMangaSelector()
    override val useNewChapterEndpoint = true
}
