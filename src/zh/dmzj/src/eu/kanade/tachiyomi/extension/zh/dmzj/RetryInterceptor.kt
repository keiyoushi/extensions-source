package eu.kanade.tachiyomi.extension.zh.dmzj

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

object RetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        repeat(2) {
            val response = chain.proceed(request)
            if (response.isSuccessful) return response
            response.close()
            Log.e("DMZJ", "failed to fetch '${request.url}': HTTP ${response.code}")
        }
        return chain.proceed(request)
    }
}
