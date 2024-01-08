package eu.kanade.tachiyomi.extension.es.tresdaosscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TresDaosScan : MangaThemesia(
    "Tres Daos Scan",
    "https://tresdaos.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val seriesStatusSelector = ".tsinfo .imptdt:contains(estado) i"
}
