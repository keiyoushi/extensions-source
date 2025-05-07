package eu.kanade.tachiyomi.extension.pt.crystalcomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class CrystalComics : MangaThemesia(
    "Crystal Comics",
    "https://crystalcomics.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale("pt", "BR")),
) {
    // Migrate from Etoshore to MangaThemesia
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
