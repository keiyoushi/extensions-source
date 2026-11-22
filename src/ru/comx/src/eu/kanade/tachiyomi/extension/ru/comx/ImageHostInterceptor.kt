package eu.kanade.tachiyomi.extension.ru.comx

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

// The site's onerror switch between its two upload hosts, keeping the one that loaded
class ImageHostInterceptor : Interceptor {

    @Volatile
    private var workingHost: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (url.host !in HOSTS || url.pathSegments.first() == "comix") return chain.proceed(request)

        val host = workingHost ?: url.host
        val response = try {
            chain.proceed(request.withHost(host))
        } catch (_: IOException) {
            null
        }
        if (response?.isSuccessful == true) return response
        response?.close()

        val otherHost = HOSTS.first { it != host }
        return chain.proceed(request.withHost(otherHost)).also {
            if (it.isSuccessful) workingHost = otherHost
        }
    }

    private fun Request.withHost(host: String): Request = newBuilder().url(url.newBuilder().host(host).build()).build()

    companion object {
        private val HOSTS = listOf("rus.com-x.life", "img.com-x.life")
    }
}
