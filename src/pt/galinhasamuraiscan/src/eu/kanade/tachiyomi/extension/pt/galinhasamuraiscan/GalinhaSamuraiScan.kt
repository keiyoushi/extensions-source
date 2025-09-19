package eu.kanade.tachiyomi.extension.pt.galinhasamuraiscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class GalinhaSamuraiScan : Madara(
    "Galinha Samurai Scan",
    "https://galinhasamurai.com",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useNewChapterEndpoint = true

    // Moved from YuYu to Madara
    override val versionId = 3
}
