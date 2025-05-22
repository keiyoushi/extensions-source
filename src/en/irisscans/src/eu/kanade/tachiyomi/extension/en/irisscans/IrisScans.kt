package eu.kanade.tachiyomi.extension.en.irisscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

class IrisScans : MangaThemesia(
    "Iris Scans",
    "https://irisscans.xyz",
    "en",
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
