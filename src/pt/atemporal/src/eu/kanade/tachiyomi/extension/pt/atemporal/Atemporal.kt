package eu.kanade.tachiyomi.extension.pt.atemporal

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class Atemporal : MangaThemesia(
    "Atemporal",
    "https://atemporal.cloud",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
