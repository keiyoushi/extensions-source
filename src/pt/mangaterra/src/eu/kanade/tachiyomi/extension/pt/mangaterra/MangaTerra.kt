package eu.kanade.tachiyomi.extension.pt.mangaterra

import eu.kanade.tachiyomi.multisrc.terrascan.TerraScan
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class MangaTerra : TerraScan(
    "Manga Terra",
    "https://manga-terra.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()
}
