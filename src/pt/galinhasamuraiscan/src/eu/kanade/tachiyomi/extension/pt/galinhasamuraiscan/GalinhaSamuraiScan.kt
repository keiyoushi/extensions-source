package eu.kanade.tachiyomi.extension.pt.galinhasamuraiscan

import eu.kanade.tachiyomi.multisrc.yuyu.YuYu
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class GalinhaSamuraiScan : YuYu(
    "Galinha Samurai Scan",
    "https://galinhasamurai.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // Moved from Madara to YuYu
    override val versionId = 2
}
