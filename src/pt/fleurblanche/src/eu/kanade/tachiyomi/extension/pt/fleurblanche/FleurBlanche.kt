package eu.kanade.tachiyomi.extension.pt.fleurblanche

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class FleurBlanche : Madara(
    "Fleur Blanche",
    "https://fbsquadx.com",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
) {

    override val client = super.client.newBuilder()
        .addInterceptor(::authWarningIntercept)
        .rateLimit(1, 2)
        .build()

    override val useNewChapterEndpoint = true

    private fun authWarningIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.toString().contains("wp-login.php")) {
            response.close()
            throw IOException("É necessário realizar o login via WebView para acessar a fonte.")
        }

        return response
    }
}
