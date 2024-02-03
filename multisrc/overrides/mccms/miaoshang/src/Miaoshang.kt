package eu.kanade.tachiyomi.extension.zh.miaoshang

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl

class Miaoshang : MCCMS(
    "喵上漫画",
    "https://www.miaoshangmanhua.com",
    "zh",
    MCCMSConfig(
        textSearchOnlyPageOne = true,
        lazyLoadImageAttr = "data-src",
    ),
) {
    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()
}
