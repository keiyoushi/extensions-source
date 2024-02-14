package eu.kanade.tachiyomi.extension.en.rudrascans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class RudraScans : Keyoapp("RudraScans", "https://rudrascans.com", "en") {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
