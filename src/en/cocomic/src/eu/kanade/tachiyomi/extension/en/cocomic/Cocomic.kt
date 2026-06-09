package eu.kanade.tachiyomi.extension.en.cocomic

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class Cocomic : Madara("Cocomic", "https://cocomic.co", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true

    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium)"
}
