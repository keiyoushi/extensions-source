package eu.kanade.tachiyomi.extension.pt.modescanlator

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimit

class ModeScanlator : HeanCms(
    "Mode Scanlator",
    "https://site.modescanlator.net",
    "pt-BR",
    "https://api.modescanlator.net",
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    // PeachScan -> HeanCms
    override val versionId = 2

    override val useNewChapterEndpoint = true
}
