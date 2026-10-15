package eu.kanade.tachiyomi.extension.all.mangafire

import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebViewBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock
import kotlin.getValue

class ChallengeSolverInterceptor(
    getCookieJar: () -> CookieJar,
    private val doSolve: () -> Boolean,
) : Interceptor {
    private val html by lazy { javaClass.getResource("/assets/solver.html")!!.readText() }

    private val lock = ReentrantReadWriteLock()

    private val cookieJar by lazy(getCookieJar)

    private fun clearance(url: HttpUrl) = cookieJar.loadForRequest(url).find { it.name == "waf_pass" }?.value

    private var clientHintsHeaders: Map<String, String> = mapOf()

    @Serializable
    private data class ErrorResponse(
        val error: String?,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val call = chain.call()
        val request = chain.request()
        val url = request.url

        val oldClearance = lock.readLock().withLock {
            // We can't just check cookies first because we might need to bypass Cloudflare
            val response = chain.proceed(
                request
                    .newBuilder()
                    .apply { clientHintsHeaders.forEach { header(it.key, it.value) } }
                    .build(),
            )
            if (
                response.code != 403 ||
                try {
                    response.peekBody(Long.MAX_VALUE).byteStream().parseAs<ErrorResponse>().error != "captcha_required"
                } catch (_: SerializationException) {
                    true
                }
            ) {
                return response
            }
            response.close()

            if (!doSolve()) {
                throw IOException("Shape-selecting captcha detected. Open in WebView to solve manually or turn on the setting to solve automatically.")
            }

            clearance(url)
        }

        if (call.isCanceled()) {
            throw IOException("Canceled")
        }

        // We are solving the challenge in a WebView instead of directly in Kotlin because the solver depends on OpenCV, which is >100 MB
        // as a Kotlin dependency. Also, the OpenCV binaries would be in the storage of the extension app, making them inaccessible to the
        // reader app.
        // Using a WebView instead makes it possible to dynamically request OpenCV.js, keeping the app size small.
        val solved = lock.writeLock().withLock {
            if (call.isCanceled()) {
                throw IOException("Canceled")
            }

            if (clearance(url).let { it != oldClearance && !it.isNullOrBlank() }) {
                // Captcha solved in another call, skip
                return@withLock true
            }

            runWebViewBlocking(call) {
                userAgent = request.header("User-Agent").orEmpty()
                interceptRequest { webResourceRequest ->
                    clientHintsHeaders = webResourceRequest
                        .requestHeaders
                        .filterKeys { it.startsWith("sec-ch-ua", ignoreCase = true) }
                    null
                }
                jsBridge("bridge") { resolve(it == "true") }
                loadData("https://${url.host}/@waf/solver", html)
            }
        }

        if (!solved) {
            throw IOException("Failed to solve shape-selecting captcha. Open in WebView to solve manually.")
        }

        return chain.proceed(
            request
                .newBuilder()
                .apply { clientHintsHeaders.forEach { header(it.key, it.value) } }
                .build(),
        )
    }
}
