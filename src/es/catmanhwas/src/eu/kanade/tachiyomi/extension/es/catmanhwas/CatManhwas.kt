package eu.kanade.tachiyomi.extension.es.catmanhwas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class CatManhwas : Madara(
    "CatManhwas",
    "https://catmanhwas.com",
    "es",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("es")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1)
        .build()
}
