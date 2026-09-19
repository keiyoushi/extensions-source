package eu.kanade.tachiyomi.extension.ar.mangaspark

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaSpark : Madara() {

    override val dateFormat = SimpleDateFormat("d MMMM، yyyy", Locale("ar"))
    override val chapterUrlSuffix = ""
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
    override val pageListParseSelector = "div.wp-manga-chapter-img img, div.reading-content img"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("User-Agent", BROWSER_USER_AGENT)
        .set("Accept-Language", "ar,en;q=0.9")
}

private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36"
