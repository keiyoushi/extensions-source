package eu.kanade.tachiyomi.extension.tr.asemifansub

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class AsemiFansub : MangaThemesia(
    "Asemi Fansub",
    "https://asemifansub.com",
    "tr",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
