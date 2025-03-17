package eu.kanade.tachiyomi.extension.es.manhuaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale

class SamuraiScan : Madara(
    "SamuraiScan",
    "https://samurai.wordoco.com",
    "es",
    SimpleDateFormat("dd MMMM, yyyy", Locale("es")),
) {
    override val id = 5713083996691468192

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true

    override val mangaSubString = "rd"

    override val client: OkHttpClient = super.client.newBuilder()
        .followRedirects(false)
        .addInterceptor(::fixFollowRedirects)
        .rateLimit(3)
        .build()

    override val mangaDetailsSelectorDescription = "div.summary_content div.manga-summary"

    // ========================== Utilities =========================

    private fun fixFollowRedirects(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val location = response.takeIf { it.isRedirect }?.headers?.get("location")
            ?: return response

        response.close()

        val sslLocation = location
            .replace(SSL_REGEX, "https")
            .replace(WWW_REGEX, "")

        mutableListOf(sslLocation, location).forEach { url ->
            val newRequest = response.request.newBuilder()
                .url(url)
                .build()

            val redirectResponse = try {
                chain.proceed(newRequest)
            } catch (e: SocketTimeoutException) {
                return@forEach
            }

            if (redirectResponse.isSuccessful.not()) {
                redirectResponse.close()
                return@forEach
            }
            return redirectResponse
        }
        return chain.proceed(chain.request())
    }

    companion object {
        val SSL_REGEX = """https?""".toRegex()
        val WWW_REGEX = """[wW]{3}\.""".toRegex()
    }
}
