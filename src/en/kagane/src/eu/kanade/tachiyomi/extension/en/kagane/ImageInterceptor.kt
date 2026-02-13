package eu.kanade.tachiyomi.extension.en.kagane

import okhttp3.Interceptor
import okhttp3.Response

open class ImageInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
}
