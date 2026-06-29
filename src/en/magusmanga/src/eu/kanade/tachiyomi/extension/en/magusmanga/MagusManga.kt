package eu.kanade.tachiyomi.extension.en.magusmanga

import eu.kanade.tachiyomi.multisrc.iken.Iken
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MagusManga : Iken() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client =
        network.client
            .newBuilder()
            .rateLimit(1) { it.host == baseUrlHost }
            .build()

    override val sortPagesByFilename = true
}
