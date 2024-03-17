package eu.kanade.tachiyomi.extension.ar.mangaflame

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaFlame : MangaThemesia(
    "Manga Flame",
    "https://arisescans.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    override val id = 1501237443119573205

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
}
