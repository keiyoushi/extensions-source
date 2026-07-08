package eu.kanade.tachiyomi.extension.zh.zerobyw

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

private const val DEFAULT_BASE_URL = "http://www.zerobyw33.com"

private const val BASE_URL_PREF = "overrideBaseUrl"
private const val LATEST_DOMAIN_URL = "https://stevenyomi.github.io/source-domains/zerobyw.txt"

var SharedPreferences.baseUrl: String
    get() = getString(BASE_URL_PREF, DEFAULT_BASE_URL)!!
    set(value) = edit().putString(BASE_URL_PREF, value).apply()

class UpdateUrlInterceptor(
    private val preferences: SharedPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val baseUrl = preferences.baseUrl
        if (!url.toString().startsWith(baseUrl)) return chain.proceed(request)

        val failedResult = try {
            val response = chain.proceed(request)
            if (response.isSuccessful) return response
            response.close()
            Result.success(response)
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            Result.failure(e)
        }

        val newUrl = try {
            val newUrl = chain.proceed(GET(LATEST_DOMAIN_URL)).body.string()
            require(newUrl != baseUrl)
            newUrl
        } catch (_: Throwable) {
            return failedResult.getOrThrow()
        }

        preferences.baseUrl = newUrl
        val (scheme, host) = newUrl.split("://")
        val retryUrl = url.newBuilder().scheme(scheme).host(host).build()
        val retryRequest = request.newBuilder().url(retryUrl).build()
        return chain.proceed(retryRequest)
    }
}
