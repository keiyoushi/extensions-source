package eu.kanade.tachiyomi.extension.zh.mangabz

import android.util.Log
import android.webkit.CookieManager
import okhttp3.Interceptor
import okhttp3.Response

class CookieInterceptor(
    private val domain: String,
    private val key: String,
    private val value: String,
) : Interceptor {

    init {
        val url = "https://$domain/"
        val cookie = "$key=$value; Domain=$domain; Path=/"
        setCookie(url, cookie)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.endsWith(domain)) return chain.proceed(request)

        val cookie = "$key=$value"
        val cookieList = request.header("Cookie")?.split("; ") ?: emptyList()
        if (cookie in cookieList) return chain.proceed(request)

        setCookie("https://$domain/", "$cookie; Domain=$domain; Path=/")
        val prefix = "$key="
        val newCookie = buildList(cookieList.size + 1) {
            cookieList.filterNotTo(this) { it.startsWith(prefix) }
            add(cookie)
        }.joinToString("; ")
        val newRequest = request.newBuilder().header("Cookie", newCookie).build()
        return chain.proceed(newRequest)
    }

    private fun setCookie(url: String, value: String) {
        try {
            CookieManager.getInstance().setCookie(url, value)
        } catch (e: Exception) {
            // Probably running on Tachidesk
            Log.e("Mangabz", "failed to set cookie", e)
        }
    }
}
