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

    override val mangaDetailsSelectorDescription: String =
        "div.description-summary div.summary__content h3 + p, div.description-summary div.summary__content:not(:has(h3)), div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt"
}
