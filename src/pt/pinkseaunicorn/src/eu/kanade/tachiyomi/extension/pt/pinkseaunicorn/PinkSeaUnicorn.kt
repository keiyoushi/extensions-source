package eu.kanade.tachiyomi.extension.pt.pinkseaunicorn

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PinkSeaUnicorn : Madara(
    "Pink Sea Unicorn",
    "https://psunicorn.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addNetworkInterceptor(::checkPasswordProtectedIntercept)
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    private fun checkPasswordProtectedIntercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.queryParameter("password-protected") != null) {
            throw IOException("Autentique-se através da WebView e tente novamente.")
        }

        return chain.proceed(chain.request())
    }

    override val useNewChapterEndpoint = true
}
