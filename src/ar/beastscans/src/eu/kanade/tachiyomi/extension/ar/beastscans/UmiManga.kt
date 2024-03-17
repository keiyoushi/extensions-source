package eu.kanade.tachiyomi.extension.ar.beastscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class UmiManga : MangaThemesia(
    "Umi Manga",
    "https://www.umimanga.com",
    "ar",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
) {
    // Beast Scans -> Umi Manga
    override val id = 6404296554681042513

    override val client = network.cloudflareClient.newBuilder()
        .readTimeout(3, TimeUnit.MINUTES)
        .build()
}
