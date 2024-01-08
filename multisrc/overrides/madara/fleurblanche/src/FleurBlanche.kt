package eu.kanade.tachiyomi.extension.pt.fleurblanche

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class FleurBlanche : Madara(
    "Fleur Blanche",
    "https://fbsquads.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor(::authWarningIntercept)
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    private fun authWarningIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.toString().contains("wp-login.php")) {
            response.close()
            throw IOException(NEED_LOGIN_ERROR)
        }

        return response
    }

    companion object {
        private const val NEED_LOGIN_ERROR =
            "É necessário realizar o login via WebView para acessar a fonte."
    }
}
