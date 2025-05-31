package eu.kanade.tachiyomi.extension.en.quantumscans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import java.util.concurrent.TimeUnit

class QuantumScans : HeanCms(
    "Quantum Scans",
    "https://quantumscans.org",
    "en",
) {
    // Moved from Keyoapp to HeanCms
    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true
}
