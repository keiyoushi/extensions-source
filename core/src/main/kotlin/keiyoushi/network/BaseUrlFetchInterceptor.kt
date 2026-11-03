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
    private val oldUrl: HttpUrl? by lazy {
        val old = baseUrl()
        fetch()
        old.takeIf { it != baseUrl() }?.toHttpUrl()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val old = oldUrl?.takeIf { it.host == request.url.host } ?: return chain.proceed(request)
        return chain.proceed(request.moveTo(old, baseUrl().toHttpUrl()))
    }

    private fun Request.moveTo(old: HttpUrl, new: HttpUrl): Request {
        val oldOrigin = old.toString().removeSuffix("/")
        val newOrigin = new.toString().removeSuffix("/")
        fun String.moved() = if (startsWith(oldOrigin)) newOrigin + removePrefix(oldOrigin) else this

        val request = this
        return newBuilder().apply {
            url(request.url.newBuilder().scheme(new.scheme).host(new.host).port(new.port).build())
            request.header("Referer")?.let { header("Referer", it.moved()) }
            request.header("Origin")?.let { header("Origin", it.moved()) }
            request.tag(FromPage::class.java)?.let { tag(FromPage::class.java, FromPage(it.page.moved())) }
        }.build()
    }
}
