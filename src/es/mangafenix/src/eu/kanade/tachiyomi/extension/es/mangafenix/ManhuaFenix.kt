package eu.kanade.tachiyomi.extension.es.mangafenix

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaFenix : Madara(
    "Manhua Fenix",
    "https://mmfenix.com",
    "es",
    SimpleDateFormat("dd MMMM, yyyy", Locale("es")),
) {

    override val id: Long = 19158964284779393

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()
}
