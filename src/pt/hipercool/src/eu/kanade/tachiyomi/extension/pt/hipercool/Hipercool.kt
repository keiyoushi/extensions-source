package eu.kanade.tachiyomi.extension.pt.hipercool

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

class Hipercool : Madara("HipercooL", "https://hiper.cool", "pt-BR") {

    // Migrated from a custom CMS to Madara.
    override val versionId = 2

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
