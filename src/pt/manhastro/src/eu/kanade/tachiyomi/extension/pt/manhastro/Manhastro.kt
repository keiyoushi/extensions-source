package eu.kanade.tachiyomi.extension.pt.manhastro

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class Manhastro :
    Madara(
        "Manhastro",
        "https://manhastro.net",
        "pt-BR",
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")),
    ),
    ConfigurableSource {

    override val mangaSubString = "lermanga"

    private val preferences: SharedPreferences = getPreferences()

    private val application: Application by lazy { Injekt.get<Application>() }

    private var showWarning: Boolean = true

    private val authCookie: Cookie by lazy {
        val cookieJson = preferences.getString(COOKIE_STORAGE_PREF, "") as String
        if (cookieJson.isBlank()) {
            return@lazy doAuth().also(::upsetCookie)
        }

        val cookieSaved = cookieJson.parseAs<Cookie>()

        return@lazy cookieSaved.takeIf { it.isExpired().not() }
            ?: doAuth().also(::upsetCookie)
    }

    private val cookieInterceptor: Interceptor by lazy {
        return@lazy when {
            authCookie.isEmpty() -> bypassInterceptor
            else -> CookieInterceptor(baseUrl.substringAfterLast("/"), listOf(authCookie.value))
        }
    }

    override val client: OkHttpClient by lazy {
        super.client.newBuilder()
            .rateLimit(1)
            .readTimeout(1, TimeUnit.MINUTES)
            .connectTimeout(1, TimeUnit.MINUTES)
            .addInterceptor { chain ->
                if (credentials.isEmpty && showWarning) {
                    showWarning = false
                    showToast("Configure suas credências em Extensões > $name > Configuração")
                }
                return@addInterceptor chain.proceed(chain.request())
            }
            .addNetworkInterceptorIf(credentials.isNotEmpty, cookieInterceptor)
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val mime = response.headers["Content-Type"]
                if (response.isSuccessful) {
                    if (mime != "application/octet-stream") {
                        return@addInterceptor response
                    }
                    // Fix image content type
                    val type = IMG_CONTENT_TYPE.toMediaType()
                    val body = response.body.bytes().toResponseBody(type)
                    return@addInterceptor response.newBuilder().body(body)
                        .header("Content-Type", IMG_CONTENT_TYPE).build()
                }
                response
            }
            .build()
    }

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val mangaDetailsSelectorTitle = "div.summary_content h2"

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override fun popularMangaRequest(page: Int): Request {
        resetToastMessage(page)
        return super.popularMangaRequest(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        resetToastMessage(page)
        return super.latestUpdatesRequest(page)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.selectFirst("script:containsData(imageLinks)")?.data()
            ?.let { imageLinksPattern.find(it)?.groups?.get(1)?.value }
            ?.let { json.decodeFromString<List<String>>(it) }
            ?.mapIndexed { i, imageUrlEncoded ->
                val imageUrl = String(Base64.decode(imageUrlEncoded, Base64.DEFAULT))
                Page(i, document.location(), imageUrl)
            } ?: emptyList()
    }

    private val imageLinksPattern = """var\s+?imageLinks\s*?=\s*?(\[.*]);""".toRegex()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val warning = "⚠️ Os dados inseridos nessa seção serão usados somente para realizar o login na fonte"
        val message = "Insira %s para prosseguir com o acesso aos recursos disponíveis na fonte"

        val usernamePref = EditTextPreference(screen.context).apply {
            title = "📧 Email"
            key = USERNAME_PREF
            summary = "Email de acesso"
            dialogMessage = buildString {
                appendLine(message.format("seu email"))
                append("\n$warning")
            }
            setDefaultValue("")

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }

        val passwordPref = EditTextPreference(screen.context).apply {
            title = "🔑 Senha"
            key = PASSWORD_PREF
            summary = "Senha de acesso"
            dialogMessage = buildString {
                appendLine(message.format("sua senha"))
                append("\n$warning")
            }
            setDefaultValue("")

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP_MESSAGE, Toast.LENGTH_LONG).show()
                true
            }
        }

        screen.addPreference(usernamePref)
        screen.addPreference(passwordPref)
    }

    // ============================ Auth ====================================

    private val credentials: Credential get() = Credential(
        email = preferences.getString(USERNAME_PREF, "") as String,
        password = preferences.getString(PASSWORD_PREF, "") as String,
    )

    private val defaultClient = network.cloudflareClient

    var attempts: Int
        get() = preferences.getInt(COOKIE_ATTEMPT, 0)
        set(value) {
            preferences.edit()
                .putInt(COOKIE_ATTEMPT, value)
                .apply()
        }
    var lastAttempt: Date?
        get() {
            val time = preferences.getLong(COOKIE_LAST_REQUEST, 0)
            if (time == 0L) {
                return null
            }
            return Date(time)
        }
        set(value) {
            val time = value?.time ?: 0L
            preferences.edit()
                .putLong(COOKIE_LAST_REQUEST, time)
                .apply()
        }

    private fun doAuth(): Cookie {
        if (credentials.isEmpty) {
            return Cookie.empty()
        }

        attempts++
        /**
         * Avoids excessive invalid requests when credentials are invalid
         */
        if (attempts >= MAX_ATTEMPT_WITHIN_PERIOD) {
            if (lastAttempt == null) {
                lastAttempt = Date()
                return Cookie.empty()
            }

            val lockPeriod = Calendar.getInstance().apply {
                time = lastAttempt!!
                add(Calendar.HOUR, 6)
            }

            when {
                Date().after(lockPeriod.time) -> {
                    attempts = 0
                    lastAttempt = null
                }
                else -> {
                    showToast("Login permitido após 6h de ${toastDateFormat.format(lastAttempt!!)}")
                    return Cookie.empty()
                }
            }
        }

        val document = defaultClient.newCall(GET(baseUrl, headers)).execute().asJsoup()
        val nonce = document.selectFirst("script#wp-manga-login-ajax-js-extra")
            ?.data()
            ?.let {
                NONCE_LOGIN_REGEX.find(it)?.groups?.get(1)?.value
            }
            ?: return Cookie.empty()

        val formHeaders = headers.newBuilder()
            .set("Accept", "*/*")
            .set("Accept-Encoding", "gzip, deflate, br")
            .set("Accept-Language", "en-US,en;q=0.9")
            .set("Connection", "keep-alive")
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val form = FormBody.Builder()
            .add("action", "wp_manga_signin")
            .add("login", credentials.email)
            .add("pass", credentials.password)
            .add("rememberme", "forever")
            .add("nonce", nonce)
            .build()

        val response = defaultClient.newCall(
            POST(
                "$baseUrl/wp-admin/admin-ajax.php",
                formHeaders,
                form,
                CacheControl.FORCE_NETWORK,
            ),
        ).execute()
        val message = """
            Falha ao acessar recurso: Usuário ou senha incorreto.
            Altere suas credências em Extensões > $name > Configuração.
        """.trimIndent()
        response.use {
            if (it.isSuccessful.not()) {
                showToast(message)
            }
        }

        return response.headers("Set-Cookie")
            .firstOrNull { it.contains("wordpress_logged_in_", ignoreCase = true) }
            ?.let(::Cookie)
            ?: Cookie.empty().also {
                showToast(message)
            }
    }

    private class Credential(
        val email: String,
        val password: String,
    ) {
        val isEmpty: Boolean get() = email.isBlank() || password.isBlank()
        val isNotEmpty: Boolean get() = isEmpty.not()
    }

    @Serializable
    private class Cookie {
        val value: Pair<String, String>
        val expired: String

        private constructor() {
            value = "" to ""
            expired = ""
        }

        constructor(setCookie: String) {
            val slices = setCookie.split("; ")
            value = slices.first().split("=").let {
                it.first() to it.last()
            }
            expired = slices.last().substringAfter("=")
        }

        fun isExpired(): Boolean =
            try { dateFormat.parse(expired)!!.before(Date()) } catch (e: Exception) { true }

        fun isEmpty(): Boolean = expired.isEmpty() || value.toList().any(String::isBlank)

        companion object {
            fun empty() = Cookie()
            private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
        }
    }

    // ============================ Utilities ====================================

    private fun OkHttpClient.Builder.addNetworkInterceptorIf(
        condition: Boolean,
        interceptor: Interceptor,
    ): OkHttpClient.Builder {
        if (condition) {
            addNetworkInterceptor(interceptor)
        }
        return this
    }

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(application, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun resetToastMessage(page: Int) {
        if (page != 1) return
        showWarning = true
    }

    val bypassInterceptor = object : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }

    private fun upsetCookie(cookie: Cookie) {
        preferences.edit()
            .putString(COOKIE_STORAGE_PREF, json.encodeToString(cookie))
            .apply()
    }

    companion object {
        private const val MAX_ATTEMPT_WITHIN_PERIOD = 2
        private const val RESTART_APP_MESSAGE = "Reinicie o aplicativo para aplicar as alterações"
        private const val USERNAME_PREF = "MANHASTRO_USERNAME"
        private const val PASSWORD_PREF = "MANHASTRO_PASSWORD"
        private const val COOKIE_STORAGE_PREF = "MANHASTRO_COOKIE_STORAGE"
        private const val COOKIE_LAST_REQUEST = "MANHASTRO_COOKIE_LAST_REQUEST"
        private const val COOKIE_ATTEMPT = "MANHASTRO_COOKIE_ATTEMPT"
        private const val IMG_CONTENT_TYPE = "image/jpeg"
        private val NONCE_LOGIN_REGEX = """"nonce":"([^"]+)""".toRegex()
        val toastDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("pt", "BR"))
    }
}
