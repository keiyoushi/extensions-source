package eu.kanade.tachiyomi.extension.pt.nexoscans

import java.util.concurrent.TimeUnit
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class NexoScans : Madara(
    "Nexo Scans",
    "https://nexoscans.com/",
    "pt",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}
