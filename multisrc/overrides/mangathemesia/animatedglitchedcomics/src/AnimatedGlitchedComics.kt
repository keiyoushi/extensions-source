package eu.kanade.tachiyomi.extension.en.animatedglitchedcomics

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class AnimatedGlitchedComics : MangaThemesia(
    "Animated Glitched Comics",
    "https://agscomics.com",
    "en",
    mangaUrlDirectory = "/series",
) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()
}
