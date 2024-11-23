package eu.kanade.tachiyomi.extension.en.cocomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class Cocomic : Madara("Cocomic", "https://cocomic.co", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium)"
}
