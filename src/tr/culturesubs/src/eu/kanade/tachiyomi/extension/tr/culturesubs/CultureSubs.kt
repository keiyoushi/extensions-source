package eu.kanade.tachiyomi.extension.tr.culturesubs

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class CultureSubs : MangaThemesia(
    "Culture Subs",
    "https://culturesubs.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
