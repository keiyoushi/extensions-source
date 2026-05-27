package eu.kanade.tachiyomi.extension.pt.erosect

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import eu.kanade.tachiyomi.network.POST
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException

internal const val LOGIN_REQUIRED_MESSAGE =
    "Configure email e senha nas configurações da fonte."

internal class AuthInterceptor(
    private val tokenProvider: AuthTokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.needsAuth() || request.header("Authorization") != null) {
            return decryptImageResponse(chain.proceed(request), request)
        }

        val authenticatedRequest = request.withAuthorization(tokenProvider.requireToken())
        val response = chain.proceed(authenticatedRequest)
        if (response.code != 401) {
            return decryptImageResponse(response, authenticatedRequest)
        }

        response.close()
        tokenProvider.clear()

        val retryRequest = request.withAuthorization(tokenProvider.requireToken())
        val retryResponse = chain.proceed(retryRequest)
        if (retryResponse.code != 401) {
            return decryptImageResponse(retryResponse, retryRequest)
        }

        retryResponse.close()
        tokenProvider.clear()
        throw IOException(LOGIN_REQUIRED_MESSAGE)
    }

    private fun decryptImageResponse(response: Response, request: Request): Response {
        if (!request.isImageApiRequest() || !response.isSuccessful || response.body.contentType()?.subtype?.contains("json") != true) {
            return response
        }

        return response.use {
            try {
                val bodyString = it.body.string()
                val payload = bodyString.parseAs<JsonObject>()

                if (!Decrypt.isEncryptedPayload(payload)) {
                    return it.newBuilder()
                        .body(bodyString.toResponseBody(it.body.contentType()))
                        .build()
                }

                it.newBuilder()
                    .body(Decrypt.imageSource(payload, tokenProvider.requireToken()).asResponseBody(WEBP_MEDIA_TYPE))
                    .build()
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw IOException("Falha na descriptografia da imagem: ${e.message}", e)
            }
        }
    }

    private fun Request.withAuthorization(token: String): Request = newBuilder().header("Authorization", "Bearer $token").build()

    private fun Request.isEroSectApiRequest(): Boolean = url.host == EROSECT_HOST && url.encodedPath.startsWith("/api/")

    private fun Request.isImageApiRequest(): Boolean = isEroSectApiRequest() && url.encodedPath.contains("/image/")

    private fun Request.needsAuth(): Boolean {
        val pathSegments = url.pathSegments
        return isEroSectApiRequest() &&
            pathSegments.size >= 5 &&
            pathSegments[1] == "obras" &&
            pathSegments[3] == "capitulos"
    }

    private companion object {
        const val EROSECT_HOST = "erosect.xyz"
        val WEBP_MEDIA_TYPE = "image/webp".toMediaType()
    }
}

internal class AuthTokenProvider(
    private val preferences: SharedPreferences,
    private val client: OkHttpClient,
    private val apiUrl: String,
    private val loginHeaders: Headers,
) {
    @Synchronized
    fun requireToken(): String = getToken().ifEmpty { throw IOException(LOGIN_REQUIRED_MESSAGE) }

    @Synchronized
    fun clear() {
        preferences.edit().remove(TOKEN_PREF).apply()
    }

    fun checkLogin(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) return

        Thread {
            val token = login(email, password)
            if (token.isNotEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        Injekt.get<Application>(),
                        "Login realizado com sucesso",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }.start()
    }

    private fun getToken(): String {
        val savedToken = preferences.getString(TOKEN_PREF, "").orEmpty()
        if (savedToken.isNotEmpty()) return savedToken

        val email = preferences.getString(EMAIL_PREF, "").orEmpty()
        val password = preferences.getString(PASSWORD_PREF, "").orEmpty()
        if (email.isEmpty() || password.isEmpty()) return ""

        return login(email, password)
    }

    private fun login(email: String, password: String): String = runCatching {
        val body = LoginRequest(
            email = email,
            password = password,
        ).toJsonRequestBody()

        client.newCall(POST("$apiUrl/auth/login", loginHeaders, body)).execute().use { response ->
            if (!response.isSuccessful) return@use ""

            response.parseAs<LoginResponse>()
                .tokenOrNull()
                ?.also { preferences.edit().putString(TOKEN_PREF, it).apply() }
                .orEmpty()
        }
    }.getOrDefault("")

    companion object {
        const val EMAIL_PREF = "email"
        const val PASSWORD_PREF = "password"
        private const val TOKEN_PREF = "token"
    }
}
