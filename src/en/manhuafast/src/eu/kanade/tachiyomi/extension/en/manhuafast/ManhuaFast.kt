package eu.kanade.tachiyomi.extension.en.manhuafast

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class ManhuaFast : Madara("ManhuaFast", "https://manhuafast.com", "en") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4.seconds)
        .build()

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
