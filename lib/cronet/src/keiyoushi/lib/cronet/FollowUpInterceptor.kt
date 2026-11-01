package keiyoushi.lib.cronet

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.ProtocolException

/** OkHttp's RetryAndFollowUpInterceptor rules for redirects, 408 and 503 */
internal class FollowUpInterceptor(private val client: OkHttpClient) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var prior: Response? = null
        var followUps = 0
        while (true) {
            var response = chain.proceed(request)
            if (prior != null) {
                response = response.newBuilder().priorResponse(prior.newBuilder().body("".toResponseBody()).build()).build()
            }
            val next = followUp(response) ?: return response
            if (++followUps > MAX_FOLLOW_UPS) throw ProtocolException("Too many follow-up requests: $followUps")
            response.close()
            prior = response
            request = next
        }
    }

    private fun followUp(response: Response): Request? = when (response.code) {
        in REDIRECTS -> redirect(response)
        408 -> response.request.takeIf {
            client.retryOnConnectionFailure &&
                it.body?.isOneShot() != true &&
                response.priorResponse?.code != 408 &&
                retryAfter(response, 0) == 0
        }
        503 -> response.request.takeIf { response.priorResponse?.code != 503 && retryAfter(response, Int.MAX_VALUE) == 0 }
        else -> null
    }

    private fun retryAfter(response: Response, default: Int): Int {
        val header = response.header("Retry-After") ?: return default
        return if (header.matches(DIGITS)) header.toInt() else Int.MAX_VALUE
    }

    private fun redirect(response: Response): Request? {
        if (!client.followRedirects) return null
        val request = response.request
        val url = response.header("Location")?.let { request.url.resolve(it) } ?: return null
        if (url.scheme != request.url.scheme && !client.followSslRedirects) return null

        return request.newBuilder().url(url).apply {
            if (request.method != "GET" && request.method != "HEAD") {
                if (response.code == 307 || response.code == 308) {
                    if (request.body?.isOneShot() == true) return null
                    method(request.method, request.body)
                } else {
                    method("GET", null)
                    removeHeader("Transfer-Encoding")
                    removeHeader("Content-Length")
                    removeHeader("Content-Type")
                }
            }
            if (url.host != request.url.host || url.port != request.url.port || url.scheme != request.url.scheme) {
                removeHeader("Authorization")
            }
        }.build()
    }

    companion object {
        private const val MAX_FOLLOW_UPS = 20
        private val REDIRECTS = setOf(300, 301, 302, 303, 307, 308)
        private val DIGITS = Regex("\\d+")
    }
}
