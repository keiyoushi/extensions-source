package eu.kanade.tachiyomi.multisrc.mangaworld

import eu.kanade.tachiyomi.network.GET
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class CookieRedirectInterceptor(private val client: OkHttpClient) : Interceptor {
    private val cookieRegex = Regex("""document\.cookie="(MWCookie[^"]+)""")

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val contentType = response.header("content-type")
        if (contentType != null && contentType.startsWith("image/", ignoreCase = true)) {
            return response
        }

        // ignore requests that already have completed the JS challenge
        if (response.headers["vary"] != null) return response

        val content = response.body.string()
        val results = cookieRegex.find(content)
            ?: return response.newBuilder().body(content.toResponseBody(response.body.contentType())).build()
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
