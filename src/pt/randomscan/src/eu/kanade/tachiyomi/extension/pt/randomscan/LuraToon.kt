package eu.kanade.tachiyomi.extension.pt.randomscan

import eu.kanade.tachiyomi.multisrc.peachscan.PeachScan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

class LuraToon : PeachScan("Lura Toon", "https://luratoon.com", "pt-BR") {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .build()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .add("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imgHeaders)
    }
}
