package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Kiryuu : NatsuId(
    "Kiryuu",
    "id",
    "https://kiryuu03.com",
) {
    // Formerly "Kiryuu (WP Manga Stream)"
    override val id = 3639673976007021338

    override fun OkHttpClient.Builder.customizeClient() = rateLimit(4)
}
