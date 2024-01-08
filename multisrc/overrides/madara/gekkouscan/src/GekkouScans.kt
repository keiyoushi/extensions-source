package eu.kanade.tachiyomi.extension.pt.gekkouscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class GekkouScans : Madara(
    "Gekkou Scans",
    "https://gekkouscans.top",
    "pt-BR",
) {

    // Theme changed from MMRCMS to Madara, again.
    override val versionId: Int = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint: Boolean = true
}
