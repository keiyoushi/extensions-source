package eu.kanade.tachiyomi.extension.pt.mangalivre

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Clears the site's reading gate for same-host requests. The gate combines a double-submit
 * `toon_v` cookie with a request signature whose format rotates between fixed and time-windowed
 * values. A 403 first refreshes the live bundle constants, then rotates the cookie, and finally
 * uses a hidden WebView as a last resort. Encrypted responses are transparently decrypted.
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
        ensureVerifyCookie()
        return proceedDecrypted(
            chain,
            request,
            signatureWindow = decryptor.currentSignatureWindow(),
            serverClockRetried = false,
            securityReloaded = false,
            cookieRotated = false,
            webViewPrimed = false,
            decryptReloaded = false,
        )
    }

    private fun proceedDecrypted(
        chain: Interceptor.Chain,
        request: Request,
        signatureWindow: Long,
        serverClockRetried: Boolean,
        securityReloaded: Boolean,
        cookieRotated: Boolean,
        webViewPrimed: Boolean,
        decryptReloaded: Boolean,
    ): Response {
        val response = chain.proceed(request.withGateHeaders(signatureWindow))

        if (response.code == 403) {
            val serverSignatureWindow = response.serverSignatureWindow()
            return when {
                !serverClockRetried && serverSignatureWindow != null && serverSignatureWindow != signatureWindow -> {
                    response.close()
                    proceedDecrypted(
                        chain,
                        request,
                        serverSignatureWindow,
                        true,
                        securityReloaded,
                        cookieRotated,
                        webViewPrimed,
                        decryptReloaded,
                    )
                }
                !securityReloaded -> {
                    response.close()
                    decryptor.reloadConstants(force = true)
                    proceedDecrypted(
                        chain,
                        request,
                        decryptor.currentSignatureWindow(),
                        serverClockRetried,
                        true,
                        cookieRotated,
                        webViewPrimed,
                        decryptReloaded,
                    )
                }
                !cookieRotated -> {
                    response.close()
                    ensureVerifyCookie(forceNew = true)
                    proceedDecrypted(
                        chain,
                        request,
                        decryptor.currentSignatureWindow(),
                        serverClockRetried,
                        securityReloaded,
                        true,
                        webViewPrimed,
                        decryptReloaded,
                    )
                }
                !webViewPrimed -> {
                    response.close()
                    primeCookie(request)
                    proceedDecrypted(
                        chain,
                        request,
                        decryptor.currentSignatureWindow(),
                        serverClockRetried,
                        securityReloaded,
                        cookieRotated,
                        true,
                        decryptReloaded,
                    )
                }
                else -> response
            }
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
        if (decryptReloaded) throw IOException(NON_JSON_MESSAGE)
        decryptor.reloadConstants(force = true)
        return proceedDecrypted(
            chain,
            request,
            decryptor.currentSignatureWindow(),
            serverClockRetried,
            securityReloaded,
            cookieRotated,
            webViewPrimed,
            true,
        )
    }

    private fun primeCookie(request: Request) {
        synchronized(this) {
            if (System.currentTimeMillis() - lastPrimeAttemptAt < REFRESH_COOLDOWN_MS) return
            lastPrimeAttemptAt = System.currentTimeMillis()
            val primePath = request.tag(ReaderPath::class.java)?.path ?: "/"
            runCatching { TokenResolver.prime("$baseUrl$primePath", userAgent) }
        }
    }

    private fun Request.withGateHeaders(signatureWindow: Long): Request {
        val verify = ensureVerifyCookie()
        return newBuilder()
            // Public API requests only need the double-submit cookie. Dropping stale session and
            // Cloudflare cookies prevents an old WebView session from poisoning reader requests.
            .header("Cookie", "$VERIFY_COOKIE=$verify")
            .header("x-toon-verify", verify)
            .header("x-toon-signature", decryptor.signature(url.encodedPath, signatureWindow))
            .build()
    }

    private fun Response.serverSignatureWindow(): Long? = headers["Date"]
        ?.let { runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull() }
        ?.toInstant()
        ?.toEpochMilli()
        ?.div(SIGNATURE_WINDOW_MS)

    private fun ensureVerifyCookie(forceNew: Boolean = false): String = synchronized(this) {
        if (!forceNew) {
            cookieClient.getCookie(baseUrl, VERIFY_COOKIE)?.let { return@synchronized it }
        }
        val value = UUID.randomUUID().toString().replace("-", "")
        val cookie = Cookie.Builder()
            .name(VERIFY_COOKIE)
            .value(value)
            .hostOnlyDomain(baseUrlHost)
            .path("/")
            .expiresAt(System.currentTimeMillis() + COOKIE_MAX_AGE_MS)
            .build()
        cookieClient.cookieJar.saveFromResponse(baseUrl.toHttpUrl(), listOf(cookie))
        cookieClient.getCookie(baseUrl, VERIFY_COOKIE) ?: value
    }

    data class ReaderPath(val path: String)

    companion object {
        private const val REFRESH_COOLDOWN_MS = 60_000L
        private const val VERIFY_COOKIE = "toon_v"
        private const val COOKIE_MAX_AGE_MS = 365L * 24 * 60 * 60 * 1_000
        private const val SIGNATURE_WINDOW_MS = 30_000L
        private const val NON_JSON_MESSAGE =
            "Não foi possível decifrar a resposta. Abra a fonte na WebView do app e tente de novo."
    }
}

private fun OkHttpClient.getCookies(baseUrl: String) = cookieJar.loadForRequest(baseUrl.toHttpUrl())

private fun OkHttpClient.getCookie(baseUrl: String, cookie: String): String? = getCookies(baseUrl).firstOrNull { it.name == cookie }?.value?.takeUnless { it.isEmpty() }
