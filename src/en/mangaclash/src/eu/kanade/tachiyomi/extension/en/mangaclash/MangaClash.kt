package eu.kanade.tachiyomi.extension.en.mangaclash

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class MangaClash :
    Madara(
        "MangaClash",
        "https://mangaclash.com",
        "en",
        dateFormat = SimpleDateFormat("MM/dd/yy", Locale.US),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 1.seconds)
        .build()
}
