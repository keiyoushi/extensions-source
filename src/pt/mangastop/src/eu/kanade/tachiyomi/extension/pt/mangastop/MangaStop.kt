package eu.kanade.tachiyomi.extension.pt.mangastop

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class MangaStop : MangaThemesia(
    "Manga Stop",
    "https://mangastop.net",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept", "application/xhtml+xml")

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
