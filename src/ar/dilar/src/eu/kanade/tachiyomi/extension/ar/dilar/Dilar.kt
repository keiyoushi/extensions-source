package eu.kanade.tachiyomi.extension.ar.dilar

import eu.kanade.tachiyomi.multisrc.gmanga.Gmanga
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class Dilar : Gmanga(
    "Dilar",
    "https://dilar.tube",
    "ar",
    "https://api.gmanga.me",
    "https://media.gmanga.me",
) {
    override val client = super.client.newBuilder()
        .rateLimit(4)
        .build()
}
