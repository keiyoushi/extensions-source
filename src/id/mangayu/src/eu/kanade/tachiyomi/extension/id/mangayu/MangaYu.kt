package eu.kanade.tachiyomi.extension.id.mangayu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MangaYu : MangaThemesia(
    "MangaYu",
    "https://mangayu.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5.seconds)
        .build()
}
