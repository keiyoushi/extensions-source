package eu.kanade.tachiyomi.extension.es.koinoboriscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.text.SimpleDateFormat
import java.util.Locale

class KoinoboriScan : Madara(
    "Koinobori Scan",
    "https://koinoboriscan.com",
    "es",
    SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1)
        .build()
}
