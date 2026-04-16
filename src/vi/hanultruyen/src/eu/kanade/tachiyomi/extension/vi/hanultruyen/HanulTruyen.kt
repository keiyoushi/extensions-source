package eu.kanade.tachiyomi.extension.vi.hanultruyen

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class HanulTruyen :
    Madara(
        "HanulTruyen",
        "https://hanultruyen.info",
        "vi",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        },
    ) {

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()
}
