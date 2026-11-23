package eu.kanade.tachiyomi.extension.es.aedex

import android.content.Context
import android.webkit.CookieManager
import android.widget.Toast
import keiyoushi.network.Fetch
import keiyoushi.network.call
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.utils.getPreferences
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.ui.askInput
import keiyoushi.utils.ui.askPassword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

internal const val PREF_ACCESS_TOKEN = "pref_access_token"

private const val LOGIN_PAGE = "auth/login"
private const val CSRF_PATH = "api/auth/csrf"
private const val CALLBACK_PATH = "api/auth/callback/credentials"
private const val SESSION_PATH = "api/auth/session"

private val ERROR_REGEX = Regex("""[?&]error=([^&]+)""")

/**
 * The `access_token` the site's own client pulls from the NextAuth session and sends as
 * `Authorization: Bearer` on the backend and reader calls.
 */
internal fun Aedex.storedAccessToken(): String? = getPreferences().getString(PREF_ACCESS_TOKEN, null)

internal fun Aedex.storeAccessToken(token: String?) {
    getPreferences().edit().apply {
        if (token == null) remove(PREF_ACCESS_TOKEN) else putString(PREF_ACCESS_TOKEN, token)
    }.apply()
}

/**
 * Logs in through NextAuth. The Cloudflare Turnstile challenge is solved in a WebView, but the
 * credentials POST goes out over OkHttp with the source's own browser headers, so the server does
 * not see a WebView (`WebViewNotAllowed`) and the solved token can be reused.
 *
 * @return the backend access token stored in the session.
 */
internal suspend fun Aedex.login(email: String, password: String): String {
    val system = client.get("$apiUrl/system", apiHeaders, Fetch.XHR).parseAs<AedexSystemDto>()
    val sitekey = system.turnstileSitekey ?: throw IOException("El sitio no publicó el sitekey de Turnstile")

    val turnstile = getTurnstileToken("$baseUrl/$LOGIN_PAGE", sitekey, action = "login")

    val csrf = client.get("$baseUrl/$CSRF_PATH", headers, Fetch.XHR).parseAs<AedexCsrfDto>().csrfToken

    val body = FormBody.Builder()
        .add("csrfToken", csrf)
        .add("callbackUrl", "$baseUrl/")
        .add("username", email.trim().lowercase())
        .add("password", password)
        .add("turnstileToken", turnstile)
        .build()
    val request = Request.Builder()
        .url("$baseUrl/$CALLBACK_PATH")
        .headers(headers.newBuilder().removeAll("X-Requested-With").build())
        .post(body)
        .fetch(Fetch.XHR)
        .build()
    // NextAuth answers with a redirect: the outcome is in Location (an error, or the callback)
    client.newBuilder().followRedirects(false).build().call(request, ensureSuccess = false).use { response ->
        val location = response.header("Location").orEmpty()
        when {
            "/auth/verify" in location -> throw IOException("Verifica tu correo en la web de Aedex antes de continuar")
            else -> ERROR_REGEX.find(location)?.groupValues?.get(1)?.let { error -> throw IOException(loginErrorMessage(error)) }
        }
    }

    val session = client.get("$baseUrl/$SESSION_PATH", headers, Fetch.XHR).parseAs<AedexSessionDto>()
    val token = session.token() ?: throw IOException("El sitio no devolvió un token de sesión")
    storeAccessToken(token)
    return token
}

private fun loginErrorMessage(error: String) = when (error) {
    "CredentialsSignin" -> "Correo o contraseña incorrectos"
    "EmailNotVerified" -> "Verifica tu correo en la web de Aedex antes de continuar"
    "WebViewNotAllowed" -> "El sitio rechazó el inicio de sesión (WebViewNotAllowed)"
    "TurnstileFailed" -> "La verificación de Cloudflare falló, inténtalo de nuevo"
    else -> "No se pudo iniciar sesión ($error)"
}

/**
 * Asks the site's session endpoint (authenticated by the session cookie) for a fresh access token.
 *
 * @return true if a new, different token was stored.
 */
internal suspend fun Aedex.refreshAccessToken(): Boolean {
    val token = runCatching {
        client.get("$baseUrl/$SESSION_PATH", headers, Fetch.XHR).parseAs<AedexSessionDto>().token()
    }.getOrNull()
    if (token == null || token == storedAccessToken()) return false
    storeAccessToken(token)
    return true
}

internal suspend fun Aedex.logout() {
    storeAccessToken(null)
    withContext(Dispatchers.IO) {
        // The session lives in the shared WebView cookie store: expire only this site's cookies
        runCatching {
            val manager = CookieManager.getInstance()
            val host = baseUrl.toHttpUrl().host
            val urls = listOf(baseUrl, "$baseUrl/api/", "$baseUrl/lector/")
            fun names() = urls.flatMap { url ->
                manager.getCookie(url).orEmpty().split(';').map { it.substringBefore('=').trim() }
            }.filter { it.isNotEmpty() }.distinct()
            names().forEach { name ->
                // `__Secure-` and `__Host-` cookies are only overwritten by a Secure one
                for (path in listOf("/", "/api", "/lector")) {
                    manager.setCookie(baseUrl, "$name=; Max-Age=0; Path=$path; Secure")
                    if (!name.startsWith("__Host-")) {
                        manager.setCookie(baseUrl, "$name=; Max-Age=0; Path=$path; Domain=$host; Secure")
                    }
                }
            }
            manager.flush()
        }
    }
}

/** Prompts for the account, logs in and reports the result. Runs off the main thread. */
internal suspend fun Aedex.promptLogin(context: Context) {
    val email = askInput(
        title = "Aedex",
        message = "Correo de tu cuenta",
        hint = "correo@ejemplo.com",
    ) ?: return
    val password = askPassword(
        title = "Aedex",
        message = "Contraseña de $email",
    ) ?: return

    login(email, password)
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Sesión iniciada en Aedex", Toast.LENGTH_SHORT).show()
    }
}
