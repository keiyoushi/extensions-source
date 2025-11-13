package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId

class Kiryuu : NatsuId(
    "Kiryuu",
    "id",
    "https://kiryuu03.com",
) {
    // Formerly "Kiryuu (WP Manga Stream)"
    override val id = 3639673976007021338

//    override val client: OkHttpClient = super.client.newBuilder()
//        .rateLimit(4)
//        .build()
}
