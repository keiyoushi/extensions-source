package eu.kanade.tachiyomi.extension.ar.mangatrend

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTrend : MangaThemesia(
    "Manga Trend",
    "https://mangaatrend.net",
    "ar",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("ar")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
