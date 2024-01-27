package eu.kanade.tachiyomi.extension.zh.manhuaren

import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class ErrorResponseInterceptor(private val baseUrl: String, private val preferences: SharedPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!request.url.toString().startsWith(baseUrl)) return response

        val body = response.body
        val content = body.string()
        if ("errorResponse" in Json.parseToJsonElement(content).jsonObject) {
            preferences.edit()
                .remove(TOKEN_PREF)
                .remove(USER_ID_PREF)
                .apply()
            throw IOException("用户ID已自动清除，请再試一次")
        }

        return response.newBuilder()
            .body(content.toResponseBody(body.contentType()))
            .build()
    }
}
