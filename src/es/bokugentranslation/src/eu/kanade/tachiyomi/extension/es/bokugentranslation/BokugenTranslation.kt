package eu.kanade.tachiyomi.extension.es.bokugentranslation

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale

class BokugenTranslation :
    MangaThemesia(
        "BokugenTranslation",
        "https://bokugents.com",
        "es",
        dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("en")),
    ) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host.endsWith(".wp.com")) {
                chain.proceed(request.newBuilder().headers(imageHeaders).build())
            } else {
                chain.proceed(request)
            }
        }
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .build()

    private val imageHeaders by lazy {
        headersBuilder()
            .set("Accept", "image/avif,image/jxl,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
            .build()
    }
}
