package eu.kanade.tachiyomi.extension.pt.yanpfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class YANPFansub : Madara(
    "YANP Fansub",
    "https://trisalyanp.com",
    "pt-BR",
    SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("pt", "BR")),
) {

    // Scanlator changed the theme from WpMangaReader to Madara.
    override val versionId: Int = 2

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

    // Page has custom link to scan website.
    override val popularMangaUrlSelector = "div.post-title a:not([target])"

    override val useNewChapterEndpoint = true
}
