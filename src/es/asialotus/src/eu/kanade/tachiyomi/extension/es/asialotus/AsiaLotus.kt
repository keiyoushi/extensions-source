package eu.kanade.tachiyomi.extension.es.asialotus

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit

class AsiaLotus : MangaThemesia(
    "Asia Lotus",
    "https://asialotuss.com",
    "es",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()
}
