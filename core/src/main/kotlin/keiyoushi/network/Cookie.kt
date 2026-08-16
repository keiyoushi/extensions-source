package keiyoushi.network

import android.webkit.CookieManager
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Adds cookies to requests matching the current source [HttpSource.baseUrl].
 */
context(source: HttpSource)
fun OkHttpClient.Builder.addCookie(
    cookies: List<Pair<String, String>>,
): OkHttpClient.Builder = addCookie({ source.baseUrl.toHttpUrl().host }, cookies)

/**
 * Adds a cookie to requests matching the current source [HttpSource.baseUrl].
 */
context(source: HttpSource)
fun OkHttpClient.Builder.addCookie(
    cookie: Pair<String, String>,
): OkHttpClient.Builder = addCookie({ source.baseUrl.toHttpUrl().host }, cookie)

/**
 * Adds dynamically resolved cookies to requests matching the current source [HttpSource.baseUrl].
 */
context(source: HttpSource)
fun OkHttpClient.Builder.addCookie(
    cookies: () -> List<Pair<String, String>>,
): OkHttpClient.Builder = addCookie({ source.baseUrl.toHttpUrl().host }, cookies)

/**
 * Adds cookies for [domain] to matching requests.
 *
 * Call this multiple times to register cookies for multiple domains. The first configuration whose
 * domain matches a request is applied.
 */
fun OkHttpClient.Builder.addCookie(
    domain: () -> String,
    cookies: List<Pair<String, String>>,
): OkHttpClient.Builder = addCookie(domain) { cookies }

/**
 * Adds a cookie for [domain] to matching requests.
 */
fun OkHttpClient.Builder.addCookie(
    domain: () -> String,
    cookie: Pair<String, String>,
): OkHttpClient.Builder = addCookie(domain, listOf(cookie))

/**
 * Adds dynamically resolved cookies for [domain] to matching requests.
 */
fun OkHttpClient.Builder.addCookie(
    domain: () -> String,
    cookies: () -> List<Pair<String, String>>,
): OkHttpClient.Builder = apply {
    val config = CookieConfig(domain, cookies)
    val existing = networkInterceptors().firstInstanceOrNull<CookieInterceptor>()
    if (existing != null) {
        existing.addConfig(config)
        return@apply
    }

    addNetworkInterceptor(CookieInterceptor(config))
}

private class CookieConfig(
    val domain: () -> String,
    val cookies: () -> List<Pair<String, String>>,
)

private class CookieInterceptor(
    config: CookieConfig,
) : Interceptor {

    private val configs = mutableListOf(config)

    init {
        setCookies(config)
    }

    fun addConfig(config: CookieConfig) {
        configs += config
        setCookies(config)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val (domain, cookies) = configs.firstNotNullOfOrNull { config ->
            val domain = config.domain()
            if (request.url.host == domain || request.url.host.endsWith(".$domain")) {
                domain to config.cookies()
            } else {
                null
            }
        } ?: return chain.proceed(request)

        setCookies(domain, cookies)

        val cookieList = request.header("Cookie")?.split("; ") ?: emptyList()
        if (cookies.all { (key, value) -> "$key=$value" in cookieList }) {
            return chain.proceed(request)
        }

        val newCookie = buildList(cookieList.size + cookies.size) {
            cookieList.filterNotTo(this) { existing ->
                cookies.any { (key, _) -> existing.startsWith("$key=") }
            }
            cookies.forEach { (key, value) -> add("$key=$value") }
        }.joinToString("; ")

        val newRequest = request.newBuilder()
            .header("Cookie", newCookie)
            .build()

        return chain.proceed(newRequest)
    }

    private fun setCookies(config: CookieConfig) {
        setCookies(config.domain(), config.cookies())
    }

    private fun setCookies(domain: String, cookies: List<Pair<String, String>>) {
        cookies.forEach { (key, value) ->
            setCookie("https://$domain/", "$key=$value; Domain=$domain; Path=/")
        }
    }

    private fun setCookie(url: String, value: String) {
        try {
            CookieManager.getInstance().setCookie(url, value)
        } catch (_: Exception) { }
    }
}
