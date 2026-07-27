package eu.kanade.tachiyomi.extension.en.omegascans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class OmegaScans : HeanCms() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        val apiUrlHost = apiUrl.toHttpUrl().host
        rateLimit(1) { it.host == apiUrlHost }
    }

    override val enableLogin = true
}
