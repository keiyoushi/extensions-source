package eu.kanade.tachiyomi.extension.en.manhwaworld

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class ManhwaWorld : Madara(
    "AQUA Scans",
    "https://aquascans.com",
    "en",
) {
    override val id = 8857833474626810640

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .build()

    override val chapterUrlSuffix = ""
}
