package eu.kanade.tachiyomi.extension.ar.mangastarz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaStarz : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMMM، yyyy", Locale.forLanguageTag("ar"))

    private val webShield = Interceptor(::passWebShield)

    // Host to when its check was last answered
    private val solvedAt = HashMap<String, Long>()

    // The answer redirects to the challenged url, which the interceptor retries itself
    private val webShieldClient by lazy {
        client.newBuilder()
            .followRedirects(false)
            .apply { interceptors().remove(webShield) }
            .addNetworkInterceptor(::hostOnlyCookie)
            .build()
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(webShield)
        .rateLimit(5) { it.host == baseUrl.toHttpUrl().host }

    private fun passWebShield(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sentAt = System.nanoTime()
        val response = chain.proceed(request)
        val challenge = response.webShieldPage()
        if (challenge == null && response.code != UNSUPPORTED_MEDIA_TYPE) return response
        response.close()
        synchronized(solvedAt) {
            val host = request.url.host
            if (solvedAt[host].let { it == null || it < sentAt }) {
                val page = challenge ?: navigationPage(request) ?: throw IOException(WEBSHIELD_ERROR)
                answerWebShield(request, page)
                solvedAt[host] = System.nanoTime()
            }
        }
        val retry = chain.proceed(request)
        if (retry.webShieldPage() != null || retry.code == UNSUPPORTED_MEDIA_TYPE) {
            retry.close()
            throw IOException(WEBSHIELD_ERROR)
        }
        return retry
    }

    private fun answerWebShield(request: Request, challenge: String) {
        val challengeUrl = WebShield.challengeUrl(challenge, request.url) ?: throw IOException(WEBSHIELD_ERROR)
        val answer = request.newBuilder().url(challengeUrl).get().fetch(Fetch.DOCUMENT.from(request.url)).build()
        webShieldClient.newCall(answer).execute().close()
    }

    // Image hosts answer image requests with 415 and show their check only to a navigation
    private fun navigationPage(request: Request): String? {
        val page = request.newBuilder().get().fetch(Fetch.DOCUMENT).build()
        return webShieldClient.newCall(page).execute().use { it.webShieldPage() }
    }

    // Every host answers with the same cookie name, so a parent-domain cookie would shadow the image hosts' own
    private fun hostOnlyCookie(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val cookies = response.headers("Set-Cookie").ifEmpty { return response }
        return response.newBuilder()
            .removeHeader("Set-Cookie")
            .apply { cookies.forEach { addHeader("Set-Cookie", it.replace(DOMAIN_ATTRIBUTE, "")) } }
            .build()
    }

    private fun Response.webShieldPage(): String? {
        if (body.contentType()?.subtype != "html") return null
        return peekBody(32L * 1024).string().takeIf(WebShield::isChallenge)
    }

    companion object {
        private const val UNSUPPORTED_MEDIA_TYPE = 415
        private val DOMAIN_ATTRIBUTE = Regex(""";\s*Domain=[^;]*""", RegexOption.IGNORE_CASE)
        private const val WEBSHIELD_ERROR = "Couldn't pass the site's bot check"
    }
}
