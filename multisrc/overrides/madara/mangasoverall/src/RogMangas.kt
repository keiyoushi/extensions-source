package eu.kanade.tachiyomi.extension.pt.mangasoverall

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class RogMangas : Madara(
    "ROG Mangás",
    "https://rogmangas.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    // Changed their name from Mangás Overall to ROG Mangás.
    override val id: Long = 7865569692125193686

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()
}
