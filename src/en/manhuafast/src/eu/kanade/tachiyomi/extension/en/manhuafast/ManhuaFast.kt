package eu.kanade.tachiyomi.extension.en.manhuafast

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ManhuaFast : Madara() {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 4.seconds)
        .build()

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
