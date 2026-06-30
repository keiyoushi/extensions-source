package eu.kanade.tachiyomi.extension.all.mangaforfree

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaForFree : Madara() {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 1.seconds)
        .build()

    override fun chapterListSelector() = when (lang) {
        "en" -> "li.wp-manga-chapter:not(:contains(Raw))"
        "ko" -> "li.wp-manga-chapter:contains(Raw)"
        else -> super.chapterListSelector()
    }
}
