package eu.kanade.tachiyomi.extension.en.omegascans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class OmegaScans : HeanCms() {
    private val apiUrlHost by lazy { apiUrl.toHttpUrl().host }

    override val client = super.client.newBuilder()
        .rateLimit(1) { it.host == apiUrlHost }
        .build()

    override val useNewChapterEndpoint = true
    override val useNewQueryEndpoint = true
    override val enableLogin = true
}
