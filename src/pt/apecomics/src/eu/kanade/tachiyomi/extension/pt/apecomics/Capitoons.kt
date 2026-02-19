package eu.kanade.tachiyomi.extension.pt.apecomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class Capitoons :
    MangaThemesia(
        "Capitoons",
        "https://capitoons.com",
        "pt-BR",
        dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {
    override val id: Long = 4475020039832513819

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2, 1)
        .build()
}
