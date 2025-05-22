package eu.kanade.tachiyomi.extension.en.quantumscans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import keiyoushi.network.rateLimit

class QuantumScans : HeanCms(
    "Quantum Scans",
    "https://quantumscans.org",
    "en",
) {
    // Moved from Keyoapp to HeanCms
    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val useNewChapterEndpoint = true
}
