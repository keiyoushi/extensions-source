package eu.kanade.tachiyomi.extension.ar.mangastarz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import okhttp3.Headers
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaStarz : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM، yyyy", Locale.forLanguageTag("ar"))

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("User-Agent", BROWSER_USER_AGENT)
        set("Accept-Language", "ar,en;q=0.9")
    }
}

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"
