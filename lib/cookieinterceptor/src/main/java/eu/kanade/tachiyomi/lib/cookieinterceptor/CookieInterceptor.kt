package eu.kanade.tachiyomi.lib.cookieinterceptor

import android.webkit.CookieManager
import okhttp3.Interceptor
import okhttp3.Response

class CookieInterceptor(
    private val domain: String,
    private val cookies: List<Pair<String, String>>
) : Interceptor {
    constructor(domain: String, cookie: Pair<String, String>) : this(domain, listOf(cookie))

    init {
        val url = "https://$domain/"
        cookies.forEach {
            val cookie = "${it.first}=${it.second}; Domain=$domain; Path=/"
            setCookie(url, cookie)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.endsWith(domain)) return chain.proceed(request)

        val cookieList = request.header("Cookie")?.split("; ") ?: emptyList()

        if (cookies.all { (key, value) -> "$key=$value" in cookieList })
            return chain.proceed(request)

        cookies.forEach { (key, value) ->
            setCookie("https://$domain/", "$key=$value; Domain=$domain; Path=/")
        }

        val newCookie = buildList(cookieList.size + cookies.size) {
            cookieList.filterNotTo(this) { existing ->
                cookies.any { (key, _) ->
                    existing.startsWith("$key=")
                }
            }
            cookies.forEach { (key, value) ->
                add("$key=$value")
            }
        }.joinToString("; ")

        val newRequest = request.newBuilder()
            .header("Cookie", newCookie)
            .build()

        return chain.proceed(newRequest)
    }

    private val cookieManager by lazy { CookieManager.getInstance() }

    private fun setCookie(url: String, value: String) {
        try {
            cookieManager.setCookie(url, value)
        } catch (_: Exception) { }
    }
}
