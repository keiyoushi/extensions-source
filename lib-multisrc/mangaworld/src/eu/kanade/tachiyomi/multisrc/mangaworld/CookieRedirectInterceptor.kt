package eu.kanade.tachiyomi.multisrc.mangaworld

import eu.kanade.tachiyomi.network.GET
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class CookieRedirectInterceptor(private val client: OkHttpClient) : Interceptor {
    private val cookieRegex = Regex("""document\.cookie="(MWCookie[^"]+)""")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        // ignore non-protected requests by checking for any header that doesn't show up when redirecting
        if (response.headers["vary"] != null) return response

        val results = cookieRegex.find(response.body.string())
            ?: return response
        val (cookieString) = results.destructured
        return chain.proceed(loadCookie(request, cookieString))
    }

    private fun loadCookie(request: Request, cookieString: String): Request {
        val cookie = Cookie.parse(request.url, cookieString)!!
        client.cookieJar.saveFromResponse(request.url, listOf(cookie))
        val headers = request.headers.newBuilder()
            .add("Cookie", cookie.toString())
            .build()
        return GET(request.url, headers)
    }
}
