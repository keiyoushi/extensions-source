package eu.kanade.tachiyomi.extension.en.suryascans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class GenzToons : MangaThemesia(
    "Genz Toons",
    "https://genztoons.com",
    "en",
) {
    override val id = 2909429739457928148

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
