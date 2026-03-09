package eu.kanade.tachiyomi.extension.en.qiscans

import eu.kanade.tachiyomi.multisrc.iken.Iken
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class QiScans :
    Iken(
        "Qi Scans",
        "en",
        "https://qimanhwa.com",
        "https://api.qimanhwa.com",
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()
}
