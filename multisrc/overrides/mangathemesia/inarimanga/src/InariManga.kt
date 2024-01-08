package eu.kanade.tachiyomi.extension.es.inarimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class InariManga : MangaThemesia(
    "InariManga",
    "https://inarimanga.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("en")),
) {

    // Site moved from Madara to MangaThemesia
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 4, 1)
        .build()
}
