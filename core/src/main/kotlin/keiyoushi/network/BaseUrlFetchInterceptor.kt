package keiyoushi.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Runs [fetch] before the first request of the app launch, then moves requests built for the old base url to the new
 * one.
 */
internal class BaseUrlFetchInterceptor(
    private val baseUrl: () -> String,
    private val fetch: () -> Unit,
) : Interceptor {
    @Volatile
    private var fetched = false

    @Volatile
    private var oldUrl: HttpUrl? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!fetched) {
            synchronized(this) {
                if (!fetched) {
                    val old = baseUrl()
                    fetch()
                    if (baseUrl() != old) oldUrl = old.toHttpUrl()
                    fetched = true
                }
            }
        }

        val request = chain.request()
        val old = oldUrl?.takeIf { it.host == request.url.host } ?: return chain.proceed(request)
        return chain.proceed(request.moveTo(old, baseUrl().toHttpUrl()))
    }

    private fun Request.moveTo(old: HttpUrl, new: HttpUrl): Request = newBuilder().apply {
        url(url.newBuilder().scheme(new.scheme).host(new.host).port(new.port).build())
        val oldOrigin = old.toString().removeSuffix("/")
        val newOrigin = new.toString().removeSuffix("/")
        for (name in listOf("Referer", "Origin")) {
            header(name)?.takeIf { it.startsWith(oldOrigin) }?.let { header(name, newOrigin + it.removePrefix(oldOrigin)) }
        }
    }.build()
}
