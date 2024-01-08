package eu.kanade.tachiyomi.extension.zh.dmzj

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

object ImageUrlInterceptor : Interceptor {

    class Tag(val url: String?)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val tag = request.tag(Tag::class) ?: return chain.proceed(request)

        try {
            val response = chain.proceed(request)
            if (response.isSuccessful) return response
            response.close()
            Log.e("DMZJ", "failed to fetch '${request.url}': HTTP ${response.code}")
        } catch (e: IOException) {
            Log.e("DMZJ", "failed to fetch '${request.url}'", e)
        }

        // this can sometimes bypass encoding issues by decoding '+' to ' '
        val decodedUrl = request.url.toString().decodePath()
        val newRequest = request.newBuilder().url(decodedUrl).build()
        try {
            val response = chain.proceed(newRequest)
            if (response.isSuccessful) return response
            response.close()
            Log.e("DMZJ", "failed to fetch '$decodedUrl': HTTP ${response.code}")
        } catch (e: IOException) {
            Log.e("DMZJ", "failed to fetch '$decodedUrl'", e)
        }

        val url = tag.url ?: throw IOException()
        val fallbackRequest = request.newBuilder().url(url).build()
        return chain.proceed(fallbackRequest)
    }
}
