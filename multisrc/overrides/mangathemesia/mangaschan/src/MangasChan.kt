package eu.kanade.tachiyomi.extension.pt.mangaschan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangasChan : MangaThemesia(
    "Mangás Chan",
    "https://mangaschan.net",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .removeAll("X-Requested-With")
                .build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("X-Requested-With", randomString((10..20).random()))

    override val altNamePrefix = "Nomes alternativos: "

    override val seriesArtistSelector = ".tsinfo .imptdt:contains(Artista) > i"
    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(Autor) > i"
    override val seriesTypeSelector = ".tsinfo .imptdt:contains(Tipo) > a"

    private fun randomString(length: Int): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return CharArray(length) { charPool.random() }.concatToString()
    }
}
