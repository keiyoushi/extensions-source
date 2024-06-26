package eu.kanade.tachiyomi.extension.pt.prazeresviolentos

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PrazeresViolentos : Madara(
    "Prazeres Violentos",
    "https://prazeresviolentos.com",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .addInterceptor(::checkPasswordProtectedIntercept)
        .build()

    private fun checkPasswordProtectedIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.queryParameter("password-protected") != null) {
            response.close()
            throw IOException("Autentique-se através da WebView e tente novamente.")
        }

        return response
    }

    override val useNewChapterEndpoint = true
}
