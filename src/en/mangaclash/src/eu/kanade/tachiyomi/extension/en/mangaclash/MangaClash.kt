package eu.kanade.tachiyomi.extension.en.mangaclash

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class MangaClash : Madara(
    "MangaClash",
    "https://toonclash.com",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yy", Locale.US),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()
}
