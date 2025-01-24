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
        // ignore non-protected requests by checking for any of the x-* headers
        if (response.headers["x-frame-options"] != null) return response
        return try {
            chain.proceed(loadCookies(request, response))
        } catch (e: Throwable) {
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that
            // we don't crash the entire app
            e.printStackTrace()
            throw IOException(e)
        }
    }

    private fun loadCookies(request: Request, response: Response): Request {
        val (cookieString) = """document\.cookie="([^"]+)""".toRegex().find(response.body.string())!!.destructured
        val cookie = Cookie.parse(request.url, cookieString)!!

        client.cookieJar.saveFromResponse(request.url, listOf(cookie))
        val headers = request.headers.newBuilder()
            .add("Cookie", cookie.toString())
            .build()

        return GET(request.url.toString(), headers)
    }
}
