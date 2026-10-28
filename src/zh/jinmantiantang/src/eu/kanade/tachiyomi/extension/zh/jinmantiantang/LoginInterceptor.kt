package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.POST
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

// The site keeps the login in its AVS session cookie, one session per account.
internal class LoginInterceptor(
    private val preferences: SharedPreferences,
    private val baseClient: OkHttpClient,
    private val baseUrlProvider: () -> String,
    private val headersProvider: () -> Headers,
) : Interceptor {

    private val loginLock = Any()

    // Unset until the site answers a login, so a network error retries on the next request.
    private var loginCredentials: Pair<String, String>? = null

    @Volatile private var loggedIn = false

    private var loginCount = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val credentials = credentials() ?: return chain.proceed(request)
        val baseUrl = baseUrlProvider()
        if (request.url.host != baseUrl.toHttpUrl().host) return chain.proceed(request)

        val count = synchronized(loginLock) {
            if (loginCredentials != credentials) login(baseUrl, credentials)
            loginCount
        }
        val response = chain.proceed(request)
        if (!loggedIn || !response.isLoggedOut()) return response

        response.close()
        synchronized(loginLock) {
            if (loginCount == count) login(baseUrl, credentials)
        }
        return chain.proceed(request)
    }

    private fun credentials(): Pair<String, String>? {
        val username = preferences.getString(USERNAME_PREF, null)?.trim()
        val password = preferences.getString(PASSWORD_PREF, null)
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) return null
        return username to password
    }

    // An ended session answers first with an empty Refresh, then with guest pages.
    private fun Response.isLoggedOut(): Boolean = header("Refresh") != null ||
        (header("Content-Type")?.startsWith("text/html") == true && GUEST_MARKER in peekBody(PEEK_BYTES).string())

    private fun login(baseUrl: String, credentials: Pair<String, String>) {
        loginCount++
        loginCredentials = null
        loggedIn = false

        val form = FormBody.Builder()
            .add("username", credentials.first)
            .add("password", credentials.second)
            .add("id_remember", "on")
            .add("login_remember", "on")
            .add("submit_login", "")
            .build()

        val loginHeaders = headersProvider().newBuilder()
            .set("Referer", "$baseUrl/login")
            .build()

        val loginRequest = POST("$baseUrl/login", loginHeaders, form)

        var response = baseClient.newCall(loginRequest).execute()
        if (response.header("Refresh") != null) {
            response.close()
            response = baseClient.newCall(loginRequest).execute()
        }
        loginCredentials = credentials
        if (response.header("Content-Type")?.startsWith("application/json") != true) {
            response.close()
            return
        }
        loggedIn = try {
            response.parseAs<LoginResponse>().status == 1
        } catch (e: SerializationException) {
            throw IOException(e)
        }
    }

    @Serializable
    private class LoginResponse(val status: Int)

    private companion object {
        // Google Analytics user type in every page's head, "Member" when logged in.
        const val GUEST_MARKER = """var dimension1 = "Guest""""
        const val PEEK_BYTES = 16L * 1024
    }
}
