package eu.kanade.tachiyomi.extension.en.darkscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class DarkScans : Madara("Dark Scans", "https://darkscans.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4)
        .build()

    override val mangaSubString = "all-series"

    override val filterNonMangaItems = false
}
