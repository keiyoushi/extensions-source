package eu.kanade.tachiyomi.extension.pt.fleurblanche

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
abstract class FleurBlanche : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
    override val chapterMode = ChapterMode.MangaAjax

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authWarningIntercept)
        .rateLimit(1, 2.seconds) { !it.encodedPath.startsWith("/wp-content/uploads/") }

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) > div.summary-content"

    private fun authWarningIntercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.request.url.toString().contains("wp-login.php")) {
            response.close()
            throw IOException("É necessário realizar o login via WebView para acessar a fonte.")
        }

        return response
    }
}
