package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MagusManga : MangaThemesia(
    "Magus Manga",
    "https://neroscans.com",
    "en",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("en")),
) {
    override val id = 7792477462646075400

    override val client = super.client.newBuilder()
        .rateLimit(1, 1, TimeUnit.SECONDS)
        .build()
}
