package eu.kanade.tachiyomi.extension.pt.mangaterra

import eu.kanade.tachiyomi.multisrc.terrascan.TerraScan
import keiyoushi.network.rateLimit
import kotlin.time.Duration.Companion.seconds

class MangaTerra : TerraScan(
    "Manga Terra",
    "https://manga-terra.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .build()
}
