package eu.kanade.tachiyomi.extension.es.bokugentranslation

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BokugenTranslation : MangaThemesia() {
    override val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("en"))
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host.endsWith(".wp.com")) {
                chain.proceed(request.newBuilder().headers(imageHeaders).build())
            } else {
                chain.proceed(request)
            }
        }
        .rateLimit(3, 1.seconds) { it.host == baseUrlHost }
        .build()

    private val imageHeaders by lazy {
        headersBuilder()
            .set("Accept", "image/avif,image/jxl,image/webp,image/png,image/svg+xml,image/*;q=0.8,*/*;q=0.5")
            .build()
    }
}
