package eu.kanade.tachiyomi.extension.all.akuma

import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.GET
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

class DDosGuardInterceptor(private val client: OkHttpClient) : Interceptor {

    private val cookieManager by lazy { CookieManager.getInstance() }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)

        // Check if DDos-GUARD is on
        if (response.code !in ERROR_CODES || response.header("Server") !in SERVER_CHECK) {
            return response
        }

        val cookies = cookieManager.getCookie(originalRequest.url.toString())
        val oldCookie = if (cookies != null && cookies.isNotEmpty()) {
            cookies.split(";").mapNotNull { Cookie.parse(originalRequest.url, it) }
        } else {
            emptyList()
        }
        val ddg2Cookie = oldCookie.firstOrNull { it.name == "__ddg2_" }
        if (!ddg2Cookie?.value.isNullOrEmpty()) {
            return response
        }

        response.close()

        val newCookie = getNewCookie(originalRequest.url)
            ?: return chain.proceed(originalRequest)

        val newCookieHeader = (oldCookie + newCookie)
            .joinToString("; ") { "${it.name}=${it.value}" }

        val modifiedRequest = originalRequest.newBuilder()
            .header("cookie", newCookieHeader)
            .build()

        return chain.proceed(modifiedRequest)
    }

    private fun getNewCookie(url: HttpUrl): Cookie? {
        val wellKnown = client.newCall(GET(wellKnownUrl))
            .execute().body.string()
            .substringAfter("'", "")
            .substringBefore("'", "")
        val checkUrl = "${url.scheme}://${url.host + wellKnown}"
        val response = client.newCall(GET(checkUrl)).execute()
        return response.header("set-cookie")?.let {
            Cookie.parse(url, it)
        }
    }

    companion object {
        private const val wellKnownUrl = "https://check.ddos-guard.net/check.js"
        private val ERROR_CODES = listOf(403)
        private val SERVER_CHECK = listOf("ddos-guard")
    }
}
