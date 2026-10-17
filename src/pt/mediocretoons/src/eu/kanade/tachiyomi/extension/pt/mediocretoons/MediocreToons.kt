package eu.kanade.tachiyomi.extension.pt.mediocretoons

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MediocreToons :
    KeiSource(),
    ConfigurableSource {

    private val apiHost = API_URL.toHttpUrl().host

    private val preferences by getPreferencesLazy()

    private val email get() = preferences.getString(EMAIL_PREF, "")!!

    private val password get() = preferences.getString(PASSWORD_PREF, "")!!

    private var token: String
        get() = preferences.getString(TOKEN_PREF, "")!!
        set(value) {
            preferences.edit().putString(TOKEN_PREF, value).apply()
        }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authIntercept).rateLimit(permits = 2, period = 1.seconds)

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host != apiHost || request.url.encodedPath == LOGIN_PATH) {
            return chain.proceed(request)
        }

        val usedToken = loginIfNeeded()
        val response = chain.proceed(request.withToken(usedToken))

        if (response.code != 401) {
            return response.throwIfVipRestricted()
        }

        response.close()
        val renewedToken = loginIfNeeded(rejectedToken = usedToken)

        return chain.proceed(request.withToken(renewedToken)).throwIfVipRestricted()
    }

    private fun Request.withToken(token: String) = newBuilder().header("Authorization", "Bearer $token").build()

    private fun Response.throwIfVipRestricted(): Response {
        if (code == 403) {
            close()
            throw IOException(VIP_ONLY_ERROR)
        }
        return this
    }

    // Concurrent requests wait here, so a token renewed by another thread is reused instead of logging in again
    @Synchronized
    private fun loginIfNeeded(rejectedToken: String? = null): String {
        val current = token
        if (current.isNotEmpty() && current != rejectedToken) return current

        return login()
    }

    private fun login(): String {
        val loginEmail = email
        val loginPassword = password

        if (loginEmail.isEmpty() || loginPassword.isEmpty()) {
            throw IOException(MISSING_CREDENTIALS_ERROR)
        }

        val body = LoginRequestDto(loginEmail.trim(), loginPassword).toJsonRequestBody()
        val response = client.newCall(POST("$API_URL$LOGIN_PATH", headers, body)).execute()

        if (!response.isSuccessful) {
            val message = runCatching { response.parseAs<ErrorDto>().message }.getOrNull()
            throw IOException(if (message.isNullOrBlank()) LOGIN_FAILED_ERROR else "$LOGIN_FAILED_ERROR Resposta do site: $message")
        }

        val session = try {
            response.parseAs<LoginDto>()
        } catch (e: SerializationException) {
            throw IOException(LOGIN_INVALID_RESPONSE_ERROR, e)
        }

        val newToken = session.token?.takeIf(String::isNotEmpty) ?: throw IOException(LOGIN_FAILED_ERROR)

        // Credentials changed while logging in: this token belongs to the previous account
        if (loginEmail != email || loginPassword != password) {
            throw IOException(LOGIN_FAILED_ERROR)
        }

        token = newToken
        return newToken
    }

    private fun clearSession() {
        preferences.edit().remove(TOKEN_PREF).apply()
    }

    override suspend fun getPopularManga(page: Int) = client.get(searchUrl(page, POPULAR_SORT)).parseAs<MangaListDto>().toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$API_URL/obras/atualizadas-recentes".toHttpUrl().newBuilder()
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("offset", ((page - 1) * LIMIT).toString())
            .addQueryParameter("formato", LATEST_FORMAT)
            .build()

        return client.get(url).parseAs<MangaListDto>().toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = client.get(searchUrl(page, filters.firstInstanceOrNull<SortFilter>()?.selected, query, filters)).parseAs<MangaListDto>().toMangasPage()

    private fun searchUrl(page: Int, sort: String?, query: String = "", filters: FilterList = FilterList()): HttpUrl {
        val url = "$API_URL/obras/buscar".toHttpUrl().newBuilder()
            .addQueryParameter("limite", LIMIT.toString())
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("temCapitulo", "true")
            .addQueryParameter("formato", filters.firstInstanceOrNull<FormatFilter>()?.selected?.takeIf(String::isNotEmpty) ?: POPULAR_FORMATS)

        if (sort != null) {
            url.addQueryParameter("ordenarPor", sort)
        }

        if (query.isNotBlank()) {
            url.addQueryParameter("string", query)
        }

        filters.firstInstanceOrNull<StatusFilter>()?.selected?.takeIf(String::isNotEmpty)?.also {
            url.addQueryParameter("status", it)
        }

        return url.build()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        // The site's own links point to the www host
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "obra") {
            return null
        }

        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null

        return client.get("$API_URL/obras/$id").parseAs<MangaDetailsDto>().toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$API_URL/obras/${manga.url.substringAfter("/obra/")}").parseAs<MangaDetailsDto>()

        return SMangaUpdate(details.toSManga(), details.toChapterList())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val details = client.get("$API_URL/capitulos/${chapter.url.substringAfter("/capitulo/")}").parseAs<ChapterDetailsDto>()

        return client.get(details.pageListUrl).parseAs<List<PageDto>>().toPageList()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(FormatFilter(), StatusFilter(), SortFilter())

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = EMAIL_PREF
            title = "E-mail"
            summary = "E-mail da conta usada para acessar o site"
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                clearSession()
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "Senha"
            summary = "Senha da conta usada para acessar o site"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { _, _ ->
                clearSession()
                true
            }
        }.also(screen::addPreference)
    }

    companion object {
        private const val API_URL = "https://back2.mediocrescan.com"
        private const val LOGIN_PATH = "/auth/login"

        private const val LIMIT = 24
        private const val POPULAR_FORMATS = "1,4,5,8,9,13"
        private const val POPULAR_SORT = "view_geral"
        private const val LATEST_FORMAT = "5"

        private const val EMAIL_PREF = "email"
        private const val PASSWORD_PREF = "password"
        private const val TOKEN_PREF = "token"

        private const val MISSING_CREDENTIALS_ERROR =
            "Configure e-mail e senha nas preferências da extensão"
        private const val LOGIN_FAILED_ERROR =
            "Falha no login. Verifique o e-mail e a senha nas preferências da extensão."
        private const val LOGIN_INVALID_RESPONSE_ERROR =
            "Falha no login. Não foi possível ler a resposta do site."
        private const val VIP_ONLY_ERROR = "O site restringe o acesso a assinantes VIP"
    }
}
