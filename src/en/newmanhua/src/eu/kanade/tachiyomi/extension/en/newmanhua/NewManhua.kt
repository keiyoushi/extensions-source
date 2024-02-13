package eu.kanade.tachiyomi.extension.en.newmanhua

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class NewManhua : Madara(
    "NewManhua",
    "https://newmanhua.com",
    "en",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorDescription: String =
        "div.description-summary div.summary__content h3 + p, div.description-summary div.summary__content:not(:has(h3)), div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt"
}
