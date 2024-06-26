package eu.kanade.tachiyomi.extension.pt.prismascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.MangasPage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.IOException
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class DemonSect : Madara(
    "Demon Sect",
    "https://dsectcomics.org",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    // Changed their name from Prisma Scans to Demon Sect.
    override val id: Long = 8168108118738519332

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val pathSegments = response.request.url.pathSegments
            if (pathSegments.contains("login")) {
                throw IOException("Faça o login na WebView para acessar o contéudo")
            }
            response
        }
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val mime = response.headers["Content-Type"]
            if (response.isSuccessful) {
                if (mime == "application/octet-stream" || mime == null) {
                    // Fix image content type
                    val type = "image/jpeg".toMediaType()
                    val body = response.body.bytes().toResponseBody(type)
                    return@addInterceptor response.newBuilder().body(body)
                        .header("Content-Type", "image/jpeg").build()
                }
            }
            response
        }
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservable() // Site returns http 404 even if the result is successful
            .map { response ->
                popularMangaParse(response)
            }
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservable() // Site returns http 404 even if the result is successful
            .map { response ->
                latestUpdatesParse(response)
            }
    }
}
