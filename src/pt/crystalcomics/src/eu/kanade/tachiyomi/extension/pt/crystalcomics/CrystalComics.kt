package eu.kanade.tachiyomi.extension.pt.crystalcomics

import eu.kanade.tachiyomi.multisrc.etoshore.Etoshore
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class CrystalComics : Etoshore(
    "Crystal Comics",
    "https://crystalcomics.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()
}
