package eu.kanade.tachiyomi.extension.en.shieldmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class ShieldManga : Madara("Shield Manga", "https://shieldmanga.io", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()

    // The website does not flag the content.
    override val filterNonMangaItems = false

    override fun chapterListSelector() = "li.wp-manga-hapter, .version-chap li"
}
