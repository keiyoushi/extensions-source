package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.natsuid.NatsuId
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.OkHttpClient

@Source
abstract class Kiryuu : NatsuId() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    override fun chapterListUrl(mangaId: String) = super.chapterListUrl(mangaId).newBuilder()
        .setQueryParameter("page", "1")
        .build()
}
