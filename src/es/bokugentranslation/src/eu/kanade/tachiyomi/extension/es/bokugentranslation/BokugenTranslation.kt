package eu.kanade.tachiyomi.extension.es.bokugentranslation

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BokugenTranslation : MangaThemesia() {

    override val datePattern = "dd MMMM, yyyy"

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host.endsWith(".wp.com")) {
                chain.proceed(request.newBuilder().headers(imageHeaders).build())
            } else {
                chain.proceed(request)
            }
        }

        rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }
    }

    private val imageHeaders get() = headersBuilder()
        .set("Accept", "image/avif,image/jxl,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
        .build()
}
