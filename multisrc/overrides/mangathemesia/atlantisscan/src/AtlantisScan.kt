package eu.kanade.tachiyomi.extension.es.atlantisscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class AtlantisScan : MangaThemesia(
    "Atlantis Scan",
    "https://scansatlanticos.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US),
) {
    // Site moved from Madara to MangaThemesia
    override val versionId = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()
}
