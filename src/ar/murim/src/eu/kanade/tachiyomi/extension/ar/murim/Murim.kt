package eu.kanade.tachiyomi.extension.ar.murim

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Response

@Source
abstract class Murim : ZeistManga() {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    // Missing popular
    override val supportsLatest = false
    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaParse(response: Response) = latestUpdatesParse(response)
}
