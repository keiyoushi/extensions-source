package eu.kanade.tachiyomi.extension.en.omegascans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl

class OmegaScans : HeanCms("Omega Scans", "https://omegascans.org", "en") {
    private val apiUrlHost by lazy { apiUrl.toHttpUrl().host }

    override val client = super.client.newBuilder()
        .rateLimit(1) { it.host == apiUrlHost }
        .build()

    // Site changed from MangaThemesia to HeanCms.
    override val versionId = 2

    override val useNewChapterEndpoint = true
    override val useNewQueryEndpoint = true
    override val enableLogin = true
}
