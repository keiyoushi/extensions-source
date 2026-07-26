package eu.kanade.tachiyomi.extension.ar.mangalek

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import keiyoushi.annotation.Source
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Mangalek : Madara() {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar"))

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val chapterUrlSuffix = ""

    private val formatTwo = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun parseChapterDate(date: String?): Long {
        date ?: return 0L

        return try {
            dateFormat.parse(date)!!.time
        } catch (_: ParseException) {
            try {
                formatTwo.parse(date)!!.time
            } catch (_: ParseException) {
                0L
            }
        }
    }

    override fun genresRequest(): Request = GET("$baseUrl/$mangaSubString/", headers)

    override fun parseGenres(document: Document): List<Genre> = document.selectFirst("div.genres")
        ?.select("a")
        .orEmpty()
        .map { a -> Genre(a.ownText()) }
}
