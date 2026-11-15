package eu.kanade.tachiyomi.extension.pt.pinkseaunicorn

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
abstract class PinkSeaUnicorn : Madara() {

    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("pt-BR"))
    override val chapterMode = ChapterMode.MangaAjax

    override fun OkHttpClient.Builder.configureClient() = apply {
        addNetworkInterceptor(::checkPasswordProtectedIntercept)
        rateLimit(1, 2.seconds)
    }

    private fun checkPasswordProtectedIntercept(chain: Interceptor.Chain): Response {
        if (chain.request().url.queryParameter("password-protected") != null) {
            throw IOException("Autentique-se através da WebView e tente novamente.")
        }

        return chain.proceed(chain.request())
    }
}
