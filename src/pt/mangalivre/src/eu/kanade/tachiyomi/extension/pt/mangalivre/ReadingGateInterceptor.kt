package eu.kanade.tachiyomi.extension.pt.mangalivre

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

/**
 * Clears the site's reading gate for same-host requests. The gate is a double-submit check: the
 * client-generated `toon_v` cookie must be echoed in the `x-toon-verify` header. On a 403 this
 * primes the cookie via a hidden WebView ([TokenResolver]); on a decrypt failure it reloads the
 * rotated constants ([decryptor]). The two retries are independent, so a request that is both gated
 * and stale-keyed still recovers.
 */
class ReadingGateInterceptor(
    private val baseUrl: String,
    private val userAgent: String?,
    private val cookieClient: OkHttpClient,
    private val decryptor: MangaLivreDecryptor,
) : Interceptor {

    private val baseUrlHost = baseUrl.toHttpUrl().host

    @Volatile
    private var lastPrimeAttemptAt = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrlHost) {
            return chain.proceed(request)
        }
        return proceedDecrypted(chain, request, primed = false, reloaded = false)
    }

    private fun proceedDecrypted(
        chain: Interceptor.Chain,
        request: Request,
        primed: Boolean,
        reloaded: Boolean,
    ): Response {
        val response = chain.proceed(request.withVerifyHeader())

        if (response.code == 403) {
            if (primed) return response
            response.close()
            primeCookie(request)
            return proceedDecrypted(chain, request, primed = true, reloaded = reloaded)
        }

        val dataKey = response.headers["x-toon-datakey"] ?: return response

        val contentType = response.body.contentType()
        val decrypted = decryptor.decrypt(response.body.string(), dataKey)
        if (decrypted != null) {
            return response.newBuilder()
                .body(decrypted.toResponseBody(contentType))
                .build()
        }

        response.close()
        if (reloaded) throw IOException(NON_JSON_MESSAGE)
        decryptor.reloadConstants()
        return proceedDecrypted(chain, request, primed = primed, reloaded = true)
    }

    private fun primeCookie(request: Request) {
        synchronized(this) {
            if (System.currentTimeMillis() - lastPrimeAttemptAt < REFRESH_COOLDOWN_MS) return
            lastPrimeAttemptAt = System.currentTimeMillis()
            val primePath = request.tag(ReaderPath::class.java)?.path ?: "/"
            runCatching { TokenResolver.prime("$baseUrl$primePath", userAgent) }
        }
    }

    private fun Request.withVerifyHeader(): Request {
        val verify = cookieClient.getCookie(baseUrl, "toon_v") ?: return this
        val pass = if (url.encodedPath.contains("/chapters")) PASS_CHAPTERS else PASS_DEFAULT
        return newBuilder()
            .header("x-toon-verify", verify)
            .header("toonlivre-pass", pass)
            .build()
    }

    data class ReaderPath(val path: String)

    companion object {
        private const val REFRESH_COOLDOWN_MS = 60_000L
        private const val PASS_CHAPTERS = "auth2028xy"
        private const val PASS_DEFAULT = "decoy99xz"
        private const val NON_JSON_MESSAGE =
            "Não foi possível decifrar a resposta. Abra a fonte na WebView do app e tente de novo."
    }
}

private fun OkHttpClient.getCookies(baseUrl: String) = cookieJar.loadForRequest(baseUrl.toHttpUrl())

private fun OkHttpClient.getCookie(baseUrl: String, cookie: String): String? = getCookies(baseUrl).firstOrNull { it.name == cookie }?.value?.takeUnless { it.isEmpty() }
