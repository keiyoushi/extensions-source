package eu.kanade.tachiyomi.extension.pt.mryaoifansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MrYaoiFansub : Madara(
    "MR Yaoi Fansub",
    "https://mrbenne.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::loginCheckIntercept)
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    private fun loginCheckIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.queryParameter("password-protected").isNullOrEmpty()) {
            return response
        }

        response.close()
        throw IOException(LOGIN_THROUGH_WEBVIEW_ERROR)
    }

    companion object {
        private const val LOGIN_THROUGH_WEBVIEW_ERROR = "Autentique-se pela WebView para usar a extensão."
    }
}
