// [AI助手声明] 本文件为 AI 助手于 2026-09-22 新增（移植自 keiyoushi/extensions-source 未合并的
// PR #19104 "Jinman Tiantang: Add account login"，作者 WindBT47，并按官方 1.6 代码结构做了适配：
// baseUrl/headers 改为惰性 provider，以支持"手动网址优先、镜像回退"的双轨 baseUrl）。
// 功能: 在插件设置中填写账号密码后，缺少会话 cookie(jmc_id) 时自动用账号密码登录，
// 登录 cookie 存入应用共享 CookieJar（与应用内置浏览器同一存储），已有的登录态不会被触碰，
// 登录失败只记日志、请求照常继续。修改清单见 G:\MotrixDown\sourcefix\fix\README.md。
package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.content.SharedPreferences
import android.util.Log
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

internal class LoginInterceptor(
    private val preferences: SharedPreferences,
    private val baseClient: OkHttpClient,
    private val baseUrlProvider: () -> String,
    private val headersProvider: () -> Headers,
) : Interceptor {

    private val loginLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (hasCredentials()) {
            val baseUrl = baseUrlProvider()
            if (request.url.host == baseUrl.toHttpUrl().host && !isLoggedIn(request.url)) {
                synchronized(loginLock) {
                    if (!isLoggedIn(request.url)) login(baseUrl)
                }
            }
        }
        return chain.proceed(request)
    }

    // jmc_id is the site's persistent login cookie, only present while logged in.
    private fun isLoggedIn(url: HttpUrl): Boolean = baseClient.cookieJar.loadForRequest(url).any { it.name == "jmc_id" }

    private fun hasCredentials(): Boolean = !preferences.getString(USERNAME_PREF, null).isNullOrEmpty() &&
        !preferences.getString(PASSWORD_PREF, null).isNullOrEmpty()

    private fun login(baseUrl: String) {
        val username = preferences.getString(USERNAME_PREF, null)!!.trim()
        val password = preferences.getString(PASSWORD_PREF, null)!!

        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("id_remember", "on")
            .add("login_remember", "on")
            .add("submit_login", "")
            .build()

        val loginHeaders = headersProvider().newBuilder()
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/login")
            .build()

        try {
            baseClient.newCall(POST("$baseUrl/login", loginHeaders, form)).execute().use { response ->
                if (isLoggedIn(baseUrl.toHttpUrl())) {
                    Log.i(TAG, "Login succeeded")
                } else {
                    Log.w(TAG, "Login failed: code=${response.code}")
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Login failed", e)
        }
    }

    private companion object {
        const val TAG = "Jinmantiantang"
    }
}
