package eu.kanade.tachiyomi.extension.zh.baozimanhua

import okhttp3.Interceptor
import okhttp3.Response

class RedirectDomainInterceptor(private val domain: String) : Interceptor {

    class Tag

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isRedirect || request.tag(Tag::class) == null) return response

        val location = response.header("Location")!!
        val newLocation = request.url.resolve(location)!!.newBuilder().host(domain).build()
        return response.newBuilder().header("Location", newLocation.toString()).build()
    }
}
