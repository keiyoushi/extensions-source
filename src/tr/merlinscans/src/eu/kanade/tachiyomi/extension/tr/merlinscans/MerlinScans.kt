package eu.kanade.tachiyomi.extension.tr.merlinscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MerlinScans : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "manga-ranking"

    override val latestUrlSlug = "manga"

    // External image hosts (e.g. ragnarscans.net) reset connections when Referer is merlinscans.org
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = super.configureClient().addInterceptor { chain ->
        val request = chain.request()
        if (request.url.host != baseUrl.toHttpUrl().host) {
            chain.proceed(request.newBuilder().removeHeader("Referer").build())
        } else {
            chain.proceed(request)
        }
    }
}
