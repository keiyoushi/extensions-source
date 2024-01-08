package eu.kanade.tachiyomi.extension.zh.baozimanhua

import okhttp3.Interceptor
import okhttp3.Response

object MissingImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.isRedirect && response.header("location") == "https://static-tw.baozimh.com/cover/404.jpg") {
            return response.newBuilder().code(404).build()
        }
        return response
    }
}
