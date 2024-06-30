package eu.kanade.tachiyomi.extension.tr.tonizutoon

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class TonizuToon : Madara(
    "TonizuToon",
    "https://tonizu.xyz",
    "tr",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorTitle = "#manga-title"

    override val mangaDetailsSelectorAuthor = ".summary-heading:contains(Yazar) ~ .summary-content"

    override val mangaDetailsSelectorStatus = ".summary-heading:contains(Durumu) ~ .summary-content"

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(::loginCheckInterceptor)
        .build()

    private fun loginCheckInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath == "/giris-uyari/") {
            throw IOException("WebView'de oturum açarak erişin")
        }
        return chain.proceed(request)
    }
}
