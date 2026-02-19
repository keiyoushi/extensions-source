package eu.kanade.tachiyomi.extension.es.nekoscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class NekoScans :
    MangaThemesia(
        "NekoScans",
        "https://nekoproject.org",
        "es",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
    ) {
    // Theme changed from ZeistManga to MangaThemesia
    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
