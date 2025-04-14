package eu.kanade.tachiyomi.extension.es.catmanhwas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class CatManhwas : Madara(
    "CatManhwas",
    "https://newcat1.xyz",
    "es",
    dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1)
        .build()
}
