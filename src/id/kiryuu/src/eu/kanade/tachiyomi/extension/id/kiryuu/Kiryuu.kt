package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class Kiryuu : NatsuId() {

    override fun OkHttpClient.Builder.customizeClient() = rateLimit(4).build().newBuilder()

    override fun chapterListRequest(manga: SManga): Request {
        val url = super.chapterListRequest(manga).url.newBuilder()
            .setQueryParameter("page", "1")
            .build()

        return GET(url, headers)
    }
}
