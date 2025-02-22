package eu.kanade.tachiyomi.extension.en.manhwafreake

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class ManhwaFreake : MangaThemesia(
    "Manhwa Freake",
    "https://manhwafreake.com",
    "en",
    mangaUrlDirectory = "/series",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
