package eu.kanade.tachiyomi.extension.id.natsu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Natsu : MangaThemesia("Natsu", "https://natsu.id", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()

    // manga details
    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        title = document.selectFirst(seriesThumbnailSelector)!!.attr("title")
    }
}
