package eu.kanade.tachiyomi.extension.pt.hentaiseason

import eu.kanade.tachiyomi.multisrc.gattsu.Gattsu
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class HentaiSeason : Gattsu(
    "Hentai Season",
    "https://hentaiseason.com",
    "pt-BR",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
