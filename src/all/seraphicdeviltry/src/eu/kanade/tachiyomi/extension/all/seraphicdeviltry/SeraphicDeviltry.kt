package eu.kanade.tachiyomi.extension.all.seraphicdeviltry

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

open class SeraphicDeviltry(
    lang: String,
    urlLang: String,
) : Madara(
    "SeraphicDeviltry",
    urlLang,
    lang,
    dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale("US")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1)
        .build()
}
