package eu.kanade.tachiyomi.extension.en.batcave

import android.webkit.CookieManager
import keiyoushi.utils.runWebViewBlocking
import okhttp3.Call
import okhttp3.Interceptor
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object DleGuardResolver {

    private const val TIMEOUT_SECONDS = 30L
    private const val POLL_INTERVAL_MS = 250L
    private const val PARALLEL_TRUST_WINDOW_MS = 5_000L
    private const val TRUST_COOKIE = "__guard_trust"

    @Volatile
    private var failedOnce = false

    @Volatile
    private var lastSolveAt = 0L

    fun interceptor(baseUrl: String): Interceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)
        if (response.request.url.pathSegments.firstOrNull() != "_c") {
            return@Interceptor response
        }
        response.close()
        val url = if (originalRequest.method == "GET") {
            originalRequest.url.toString()
        } else {
            "$baseUrl/"
        }
        if (!solve(url, originalRequest.header("User-Agent"), chain.call())) {
            throw IOException("Open in WebView to bypass site protection")
        }
        chain.proceed(originalRequest)
    }

    @Synchronized
    private fun solve(siteUrl: String, userAgent: String?, call: Call): Boolean {
        if (failedOnce) return false
        if (System.currentTimeMillis() - lastSolveAt < PARALLEL_TRUST_WINDOW_MS) return true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setCookie(siteUrl, "$TRUST_COOKIE=; Max-Age=0; Path=/")

        return try {
            runWebViewBlocking<Unit>(call, timeout = TIMEOUT_SECONDS.seconds) {
                this.userAgent = userAgent!!
                blockImages = true

                onPageFinished { url ->
                    poll(POLL_INTERVAL_MS.milliseconds) {
                        if (hasTrust(cookieManager, url)) {
                            resolve(Unit)
                        }
                    }
                }
                loadUrl(siteUrl)
            }
            val solved = hasTrust(cookieManager, siteUrl)
            if (solved) {
                lastSolveAt = System.currentTimeMillis()
            } else {
                failedOnce = true
            }
            solved
        } catch (e: Exception) {
            failedOnce = true
            false
        }
    }

    private fun hasTrust(cookieManager: CookieManager, url: String): Boolean {
        val cookies = cookieManager.getCookie(url) ?: return false
        return cookies.split(';').any { it.trim().startsWith("$TRUST_COOKIE=") }
    }
}
