package eu.kanade.tachiyomi.extension.pt.apecomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class ApeComics : MangaThemesia(
    "ApeComics",
    "https://apecomics.net",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
