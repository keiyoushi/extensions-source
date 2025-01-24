package eu.kanade.tachiyomi.multisrc.mangaworld

import eu.kanade.tachiyomi.network.GET
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ShittyRedirectionInterceptor(private val client: OkHttpClient) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        // ignore non-protected requests by checking for any header that doesn't show up when redirecting
        if (response.headers["vary"] != null) return response

        return try {
            val results = """document\.cookie="(MWCookie[^"]+)""".toRegex().find(response.body.string())
                ?: return response
            val (cookieString) = results.destructured
            chain.proceed(loadCookie(request, cookieString))
        } catch (e: Throwable) {
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
            // we don't crash the entire app
            e.printStackTrace()
            throw IOException(e)
        }
    }

    private fun loadCookie(request: Request, cookieString: String): Request {
        val cookie = Cookie.parse(request.url, cookieString)!!
        client.cookieJar.saveFromResponse(request.url, listOf(cookie))
        val headers = request.headers.newBuilder()
            .add("Cookie", cookie.toString())
            .build()
        return GET(request.url.toString(), headers)
    }
}
