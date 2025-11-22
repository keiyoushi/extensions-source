package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interceptor that warms up the client by making a request to the base URL
 * when encountering HTTP error status codes (>400). This helps solve Cloudflare
 * challenges before retrying the original request.
 */
class CloudflareWarmupInterceptor(
    private val baseUrl: String,
    private val headers: Headers,
) : Interceptor {

    private val isWarmedUp = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful && !isWarmedUp.get()) {
            response.close()

            try {
                val warmupRequest = GET(baseUrl, headers)
                val warmupResponse = chain.proceed(warmupRequest)
                warmupResponse.close()
                isWarmedUp.set(true)
            } catch (_: Exception) {
            }

            return chain.proceed(request)
        }

        return response
    }

    fun reset() {
        isWarmedUp.set(false)
    }
}
