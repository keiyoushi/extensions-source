package eu.kanade.tachiyomi.extension.ja.rawinu

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class RawINU : FMReader(
    "RawINU",
    "https://rawinu.com",
    "ja",
) {
    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // =========================== Manga Details ============================
    override val infoElementSelector = "div.card-body div.row"
}
