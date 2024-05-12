package eu.kanade.tachiyomi.extension.tr.athenamanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class AthenaManga : MangaThemesia(
    "Athena Manga",
    "https://athenamanga.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
