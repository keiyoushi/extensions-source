package eu.kanade.tachiyomi.extension.pt.spectralscan

import eu.kanade.tachiyomi.multisrc.yuyu.YuYu
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class SpectralScan : YuYu(
    "Spectral Scan",
    "https://spectralscan.xyz",
    "pt-BR",
) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    // Moved from Madara to YuYu
    override val versionId = 2
}
