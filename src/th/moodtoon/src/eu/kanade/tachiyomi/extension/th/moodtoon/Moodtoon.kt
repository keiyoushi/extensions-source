package eu.kanade.tachiyomi.extension.th.moodtoon

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class Moodtoon : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("th")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Bangkok")
    }
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        // Add 'color' badge as a genre
        if (document.selectFirst(".thumb .colored") != null) {
            genre = genre?.plus(", Color")
        }
    }
}
