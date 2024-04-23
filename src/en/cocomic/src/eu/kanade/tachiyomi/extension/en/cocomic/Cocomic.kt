package eu.kanade.tachiyomi.extension.en.cocomic

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Cocomic : Madara("Cocomic", "https://cocomic.co", "en") {

        override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
}
