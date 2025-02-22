package eu.kanade.tachiyomi.extension.pt.slimeread

import eu.kanade.tachiyomi.multisrc.slimereadtheme.SlimeReadTheme
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class SlimeRead : SlimeReadTheme(
    "SlimeRead",
    "https://slimeread.com",
    "pt-BR",
) {
    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
