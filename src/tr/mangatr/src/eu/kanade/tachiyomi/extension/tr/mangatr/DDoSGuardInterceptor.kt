package eu.kanade.tachiyomi.extension.tr.mangatr

import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class DDoSGuardInterceptor(private val client: OkHttpClient) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        // DDoS-Guard sometimes returns a 200 OK with a JavaScript challenge instead of a 403.
        val isDdosGuard = response.code == 403 ||
            response.header("Server")?.contains("ddos-guard") == true ||
            (response.code == 200 && response.peekBody(4096).string().contains("check.ddos-guard.net/check.js"))

        // Check if DDos-GUARD is on
        if (!isDdosGuard) {
            return response
        }

        response.close()

        val wellKnown = client.newCall(GET(WELL_KNOWN_URL, originalRequest.headers))
            .execute().use {
                it.body.string()
                    .substringAfter("'", "")
                    .substringBefore("'", "")
            }

        if (wellKnown.isNotBlank()) {
            val path = if (wellKnown.startsWith("/")) wellKnown else "/$wellKnown"
            val checkUrl = "${originalRequest.url.scheme}://${originalRequest.url.host}$path"

            // By executing this call, OkHttp's internal CookieJar automatically
            // captures and saves the clearance Set-Cookie to the original host.
            client.newCall(GET(checkUrl, originalRequest.headers)).execute().close()
        }

        // Re-execute the original request with the injected cookie applied natively.
        return chain.proceed(originalRequest)
    }

    companion object {
        private const val WELL_KNOWN_URL = "https://check.ddos-guard.net/check.js"
    }
}
