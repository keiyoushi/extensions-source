package eu.kanade.tachiyomi.extension.es.catmanhwas

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class CatManhwas : Madara(
    "CatManhwas",
    "https://newcat1.xyz",
    "es",
    dateFormat = SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("es")),
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()
}
