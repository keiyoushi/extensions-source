package eu.kanade.tachiyomi.extension.pt.maidscan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

class MaidScan : GreenShit(
    "Maid Scan",
    "https://novo.empreguetes.site",
    "pt-BR",
    scanId = 3,
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
