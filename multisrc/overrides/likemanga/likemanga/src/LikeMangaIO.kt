package eu.kanade.tachiyomi.extension.en.likemanga

import eu.kanade.tachiyomi.multisrc.likemanga.LikeManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class LikeMangaIO : LikeManga("LikeManga", "https://likemanga.io", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2)
        .build()
}
