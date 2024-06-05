package eu.kanade.tachiyomi.extension.es.housemangas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class VisorMangas : Madara(
    "Visor Mangas",
    "https://visormanga.xyz",
    "es",
    dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val popularMangaUrlSelector = "div.post-title a[href^=$baseUrl]"
}
