package eu.kanade.tachiyomi.extension.pt.mangakun

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class MangaKun : MangaThemesia(
    "Mangá Kun",
    "https://mangakun.com.br",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
