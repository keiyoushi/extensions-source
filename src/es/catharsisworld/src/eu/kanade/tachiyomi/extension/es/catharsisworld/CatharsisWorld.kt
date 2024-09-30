package eu.kanade.tachiyomi.extension.es.catharsisworld

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class CatharsisWorld : MangaThemesia(
    "Catharsis World",
    "https://catharsisworld.com",
    "es",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
