package eu.kanade.tachiyomi.extension.en.dragontea

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class DragonTea : Madara(
    "DragonTea",
    "https://dragontea.ink",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val mangaSubString = "novel"

    override val useNewChapterEndpoint = true
}
