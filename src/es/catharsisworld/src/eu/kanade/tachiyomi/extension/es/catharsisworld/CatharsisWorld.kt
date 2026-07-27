package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.seconds

@Source
abstract class CatharsisWorld :
    Madara(),
    ConfigurableSource {
    override val dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es"))

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private val preferences by getPreferencesLazy()

    private val loginLock = ReentrantLock()

    override val mangaSubString = "serie"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val client by lazy {
        super.client.newBuilder()
            .addInterceptor(::loginInterceptor)
            .rateLimit(3, 1.seconds) { it.host == baseUrlHost }
            .build()
    }

    override fun popularMangaSelector() = "div.latest-poster"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("a[style].bg-cover")?.imageFromStyle()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun searchMangaSelector() = "button.group > div.grid"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("div[style].bg-cover")?.imageFromStyle()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override val mangaDetailsSelectorTitle = "div.wp-manga div.grid > h1"
    override val mangaDetailsSelectorStatus = "div.wp-manga div[alt=type]:eq(0) > span"
    override val mangaDetailsSelectorGenre = "div.wp-manga div[alt=type]:gt(0) > span"
    override val mangaDetailsSelectorDescription = "div.wp-manga div#expand_content"

    override fun chapterListSelector() = "ul#list-chapters li > a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst("div.grid > span")!!.text()
        date_upload = element.selectFirst("div.grid > div")?.text()?.let { parseChapterDate(it) } ?: 0
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override val chapterProtectorPasswordPrefix = "protectornonce='"
    override val chapterProtectorDataPrefix = "_data='"

    private fun Element.imageFromStyle(): String = this.attr("style").substringAfter("url(").substringBefore(")")

    // =============================== Login ================================

    private fun loginInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.encodedPath == LOGIN_PATH) {
            return chain.proceed(request)
        }

        val username = preferences.getString(PREF_USERNAME, "").orEmpty()
        val password = preferences.getString(PREF_PASSWORD, "").orEmpty()

        // The gate only applies to series and chapter pages; a 404 anywhere else
        // (a missing thumbnail, another host) is a real 404.
        val gated = request.url.host == baseUrlHost &&
            request.url.encodedPath.startsWith("/$mangaSubString/")

        if (username.isBlank() || password.isBlank()) {
            val response = chain.proceed(request)

            if (!gated || response.code != NOT_FOUND) return response

            response.close()
            throw IOException(MISSING_CREDENTIALS_MESSAGE)
        }

        login(chain, username, password, force = false)

        val response = chain.proceed(request)

        if (!gated || response.code != NOT_FOUND) return response

        // The site answers 404 instead of redirecting to the login form, so this is
        // indistinguishable from an expired cookie: force a login and retry once.
        response.close()
        login(chain, username, password, force = true)

        val retry = chain.proceed(request)

        if (retry.code != NOT_FOUND) return retry

        retry.close()
        throw IOException(INVALID_SESSION_MESSAGE)
    }

    private fun login(chain: Interceptor.Chain, username: String, password: String, force: Boolean) {
        val previous = sessionCookie()

        if (!force && previous != null) return

        loginLock.withLock {
            val current = sessionCookie()
            // When forcing, an existing cookie is not enough - it is the stale one.
            // What matters is whether another thread refreshed it while we waited.
            val handledByAnotherThread = if (force) current != previous else current != null

            if (handledByAnotherThread) return

            val body = FormBody.Builder()
                .add("log", username)
                .add("pwd", password)
                .add("rememberme", "forever")
                .add("wp-submit", "Acceder")
                .add("redirect_to", "$baseUrl/")
                .build()

            // "testcookie" is omitted on purpose: WordPress only demands the test
            // cookie when the form submits that field, and sending it without having
            // fetched /wp-login.php first makes it reject the login.
            val error = chain.proceed(POST("$baseUrl$LOGIN_PATH", headers, body))
                .use { it.asJsoup().selectFirst("div#login_error")?.text() }

            if (error != null) {
                throw IOException(error)
            }
        }
    }

    private fun sessionCookie(): String? = network.client.cookieJar
        .loadForRequest(baseUrl.toHttpUrl())
        .firstOrNull { it.name.startsWith(SESSION_COOKIE_PREFIX) }
        ?.value

    // ============================= Preferences ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_USERNAME
            title = "Usuario o correo electrónico"
            summary = "El sitio reserva la lectura de capítulos a usuarios registrados. " +
                "Crea la cuenta en la web y escribe aquí los datos de acceso."
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Contraseña"
            summary = "Contraseña de tu cuenta en el sitio."
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_USERNAME = "pref_username"
        private const val PREF_PASSWORD = "pref_password"
        private const val LOGIN_PATH = "/wp-login.php"
        private const val SESSION_COOKIE_PREFIX = "wordpress_logged_in_"
        private const val NOT_FOUND = 404
        private const val MISSING_CREDENTIALS_MESSAGE =
            "Este contenido es solo para usuarios registrados. Introduce tu usuario " +
                "y contraseña en los ajustes de la extensión."
        private const val INVALID_SESSION_MESSAGE =
            "No se pudo acceder al contenido con la sesión iniciada. Comprueba tu " +
                "usuario y contraseña en los ajustes de la extensión."
    }
}
