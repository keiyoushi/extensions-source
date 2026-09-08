package eu.kanade.tachiyomi.extension.pt.hotcabaretscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HotCabaretScan : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale("pt", "BR"))

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::checkPasswordProtectedIntercept)
        .rateLimit(1, 2.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }

    private fun checkPasswordProtectedIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.queryParameter("password-protected") != null) {
            response.close()
            throw IOException("Autentique-se através da WebView e tente novamente.")
        }

        return response
    }
}
