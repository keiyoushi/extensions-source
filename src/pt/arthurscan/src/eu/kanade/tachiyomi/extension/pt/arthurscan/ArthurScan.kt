package eu.kanade.tachiyomi.extension.pt.arthurscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.network.rateLimit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class ArthurScan : Madara(
    "Arthur Scan",
    "https://arthurscan.xyz",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2.seconds)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val mime = response.headers["Content-Type"]
            if (response.isSuccessful) {
                if (mime == "application/octet-stream" || mime == null) {
                    val type = "image/jpeg".toMediaType()
                    val body = response.body.bytes().toResponseBody(type)
                    return@addInterceptor response.newBuilder().body(body)
                        .header("Content-Type", "image/jpeg").build()
                }
            }
            response
        }
        .build()

    override val useNewChapterEndpoint = true
}
