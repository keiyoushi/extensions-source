package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Kiryuu : MangaThemesia("Kiryuu", "https://kiryuu.org", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))) {
    // Formerly "Kiryuu (WP Manga Stream)"
    override val id = 3639673976007021338

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    // manga details
    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        title = document.selectFirst(seriesThumbnailSelector)!!.attr("title")
    }

    override val hasProjectPage = true
}
