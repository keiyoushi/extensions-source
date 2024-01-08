package eu.kanade.tachiyomi.extension.zh.zerobyw

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

private const val DEFAULT_BASE_URL = "http://www.zerobyw007.com"

private const val BASE_URL_PREF = "ZEROBYW_BASEURL"
private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
private const val LATEST_DOMAIN_URL = "https://stevenyomi.github.io/source-domains/zerobyw.txt"

var SharedPreferences.baseUrl: String
    get() = getString(BASE_URL_PREF, DEFAULT_BASE_URL)!!
    set(value) = edit().putString(BASE_URL_PREF, value).apply()

fun SharedPreferences.clearOldBaseUrl(): SharedPreferences {
    if (getString(DEFAULT_BASE_URL_PREF, "")!! == DEFAULT_BASE_URL) return this
    edit()
        .remove(BASE_URL_PREF)
        .putString(DEFAULT_BASE_URL_PREF, DEFAULT_BASE_URL)
        .apply()
    return this
}

fun getBaseUrlPreference(context: Context) = EditTextPreference(context).apply {
    key = BASE_URL_PREF
    title = "网址"
    summary = "正常情况下会自动更新。" +
        "如果出现错误，请在 GitHub 上报告，并且可以在 GitHub 仓库 zerozzz123456/1 找到最新网址手动填写。" +
        "填写时按照 $DEFAULT_BASE_URL 格式。"
    setDefaultValue(DEFAULT_BASE_URL)

    setOnPreferenceChangeListener { _, newValue ->
        try {
            checkBaseUrl(newValue as String)
            true
        } catch (_: Throwable) {
            Toast.makeText(context, "网址格式错误", Toast.LENGTH_LONG).show()
            false
        }
    }
}

fun ciGetUrl(client: OkHttpClient): String {
    return try {
        val response = client.newCall(GET(LATEST_DOMAIN_URL)).execute()
        response.body.string()
    } catch (e: Throwable) {
        println("::error ::Zerobyw: Failed to fetch latest URL")
        e.printStackTrace()
        DEFAULT_BASE_URL
    }
}

private fun checkBaseUrl(url: String) {
    require(url == url.trim() && !url.endsWith('/'))
    val pathSegments = url.toHttpUrl().pathSegments
    require(pathSegments.size == 1 && pathSegments[0].isEmpty())
}

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
