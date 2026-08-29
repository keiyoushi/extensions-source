package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.iken.Iken
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MagusManga : Iken() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1) { it.host == baseUrlHost }
    }

    override val sortPagesByFilename = true
}
