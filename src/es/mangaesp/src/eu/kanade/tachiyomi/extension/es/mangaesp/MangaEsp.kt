package eu.kanade.tachiyomi.extension.es.mangaesp

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

class MangaEsp : MangaThemesia(
    "MangaEsp",
    "https://mangaesp.topmanhuas.org",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("en")),
) {
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(baseUrl.toHttpUrl(), 3)
        .build()

    override val hasProjectPage = true

    override val projectPageString = "/proyectos"
}
