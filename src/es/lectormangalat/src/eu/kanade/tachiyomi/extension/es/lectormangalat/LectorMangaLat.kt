package eu.kanade.tachiyomi.extension.es.lectormangalat

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class LectorMangaLat : Madara(
    "LectorManga.lat",
    "https://www.lectormanga.lat",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val mangaSubString = "biblioteca"

    override val useNewChapterEndpoint = true

    override val pageListParseSelector = "div.reading-content div.page-break > img"
}
