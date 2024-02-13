package eu.kanade.tachiyomi.extension.fr.perfscan

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class PerfScan : HeanCms("Perf Scan", "https://perf-scan.fr", "fr") {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 1, 2)
        .build()

    override val coverPath: String = ""
    override val useNewQueryEndpoint = true

    override val slugStrategy = SlugStrategy.ID
}
