package eu.kanade.tachiyomi.extension.zh.manhuaren

import android.content.SharedPreferences
import eu.kanade.tachiyomi.extension.zh.manhuaren.Manhuaren.Companion.TOKEN_PREF
import eu.kanade.tachiyomi.extension.zh.manhuaren.Manhuaren.Companion.USER_ID_PREF
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
            throw IOException("请求出错，用户ID已自动清除。请尝试以下其一后再试：\n选项1：打开漫画人官方APP（不用登录账号）\n选项2：从其他设备拷贝用户ID和Token到本插件设置里")
        }

        return response.newBuilder()
            .body(content.toResponseBody(body.contentType()))
            .build()
    }
}
