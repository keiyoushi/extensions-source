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

/**
 * Logs in to the site with the credentials saved in the source settings when they are set
 * and the session cookie is missing, so that login-gated content can be accessed. Cookies
 * from the login response are stored in the app's shared cookie jar (the same store the
 * in-app WebView uses) and persist across restarts. An existing session is never touched,
 * and a failed login is only logged: requests continue with whatever session exists.
 */
internal class LoginInterceptor(
    private val baseUrl: String,
    private val headers: Headers,
    private val preferences: SharedPreferences,
    private val baseClient: OkHttpClient,
) : Interceptor {

    private val baseHttpUrl = baseUrl.toHttpUrl()
    private val loginLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host == baseHttpUrl.host && hasCredentials() && !isLoggedIn(request.url)) {
            synchronized(loginLock) {
                if (!isLoggedIn(request.url)) login()
            }
        }
        return chain.proceed(request)
    }

    // jmc_id is the site's persistent login cookie, only present while logged in.
    private fun isLoggedIn(url: HttpUrl): Boolean = baseClient.cookieJar.loadForRequest(url).any { it.name == "jmc_id" }

    private fun hasCredentials(): Boolean = !preferences.getString(USERNAME_PREF, null).isNullOrEmpty() &&
        !preferences.getString(PASSWORD_PREF, null).isNullOrEmpty()

    private fun login() {
        val username = preferences.getString(USERNAME_PREF, null)!!.trim()
        val password = preferences.getString(PASSWORD_PREF, null)!!

        val form = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .add("id_remember", "on")
            .add("login_remember", "on")
            .add("submit_login", "")
            .build()

        val loginHeaders = headers.newBuilder()
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/login")
            .build()

        try {
            baseClient.newCall(POST("$baseUrl/login", loginHeaders, form)).execute().use { response ->
                if (isLoggedIn(baseHttpUrl)) {
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
