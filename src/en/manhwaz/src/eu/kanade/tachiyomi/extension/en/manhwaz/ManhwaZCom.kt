package eu.kanade.tachiyomi.extension.en.manhwaz

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

class ManhwaZCom : ManhwaZ(
    "ManhwaZ",
    "https://manhwaz.com",
    "en",
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
