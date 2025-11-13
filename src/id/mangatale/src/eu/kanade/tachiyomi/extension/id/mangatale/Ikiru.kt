package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient

class Ikiru : NatsuId(
    "Ikiru",
    "id",
    "https://02.ikiru.wtf",
) {
    // Formerly "MangaTale"
    override val id = 1532456597012176985

//    override val client: OkHttpClient = super.client.newBuilder()
//        .rateLimit(12, 3)
//        .build()
}
