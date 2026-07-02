package eu.kanade.tachiyomi.extension.pt.arthurscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ArthurScan : Madara() {
    override val dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR"))

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val mime = response.headers["Content-Type"]
            if (response.isSuccessful) {
                if (mime == "application/octet-stream" || mime == null) {
                    val type = "image/jpeg".toMediaType()
                    val body = response.body.source().asResponseBody(type)
                    return@addInterceptor response.newBuilder().body(body).build()
                }
            }
            response
        }
        .rateLimit(1, 2.seconds)
        .build()

    override val useNewChapterEndpoint = true
}
