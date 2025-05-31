package eu.kanade.tachiyomi.extension.pt.plumacomics

import eu.kanade.tachiyomi.multisrc.yuyu.YuYu
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class PlumaComics : YuYu(
    "Pluma Comics",
    "https://new.plumacomics.cloud",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // Moved from Madara to YuYu
    override val versionId = 3
}
