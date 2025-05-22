package eu.kanade.tachiyomi.extension.pt.mangabr

import eu.kanade.tachiyomi.multisrc.terrascan.TerraScan
import keiyoushi.network.rateLimit
import kotlin.time.Duration.Companion.seconds

class MangaBR : TerraScan(
    "Manga BR",
    "https://mangabr.net",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
