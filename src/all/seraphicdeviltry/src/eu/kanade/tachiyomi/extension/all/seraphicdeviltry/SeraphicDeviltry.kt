package eu.kanade.tachiyomi.extension.all.seraphicdeviltry

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class SeraphicDeviltry(
    lang: String,
    baseUrl: String,
) : Madara(
    "SeraphicDeviltry",
    baseUrl,
    lang,
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale("US")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
