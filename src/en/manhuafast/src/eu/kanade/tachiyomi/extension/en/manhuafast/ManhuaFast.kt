package eu.kanade.tachiyomi.extension.en.manhuafast

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ManhuaFast : Madara("ManhuaFast", "https://manhuafast.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4, TimeUnit.SECONDS)
        .build()

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
