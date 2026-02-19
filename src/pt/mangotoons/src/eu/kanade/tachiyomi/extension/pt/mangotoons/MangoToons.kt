package eu.kanade.tachiyomi.extension.pt.mangotoons

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangoToons :
    HttpSource(),
    ConfigurableSource {

    override val name = "Mango Toons"

    override val baseUrl = "https://mangotoons.com"

    private val cdnUrl = "https://cdn.mangotoons.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val json: Json = Injekt.get<Json>()

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestUrl = originalRequest.url.toString()

        if (requestUrl.contains("/api/") && !requestUrl.contains("/api/auth/login")) {
            val token = getToken()
            if (token.isNotEmpty()) {
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
                val response = chain.proceed(newRequest)
                if (response.code == 401) {
                    response.close()
                    preferences.edit().remove(PREF_TOKEN).apply()
                    throw java.io.IOException("Faça o login nas configurações")
                }
                return@Interceptor response
            }
        }

        val response = chain.proceed(originalRequest)
        if (response.code == 401) {
            response.close()
            throw java.io.IOException("Faça o login nas configurações")
        }
        response
    }

    private fun getToken(): String {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            return ""
        }

        val token = preferences.getString(PREF_TOKEN, "") ?: ""
        if (token.isNotEmpty()) return token

        return login(email, password)
    }

    private fun login(email: String, password: String): String = try {
        val payload = AuthRequestDto(email, password)
        val body = json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)
        val request = POST("$baseUrl/api/auth/login", headers, body)
        val response = network.client.newCall(request).execute()
        if (response.isSuccessful) {
            val loginResponse = response.parseAs<LoginResponseDto>()
            if (loginResponse.sucesso && loginResponse.token != null) {
                preferences.edit().putString(PREF_TOKEN, loginResponse.token).apply()
                loginResponse.token
            } else {
                response.close()
                ""
            }
        } else {
            response.close()
            ""
        }
    } catch (e: Exception) {
        ""
    }

    private fun checkLogin(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) return

        Thread {
            val token = login(email, password)
            if (token.isNotEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(Injekt.get<Application>(), "Login realizado com sucesso", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(CookieInterceptor(baseUrl.toHttpUrl().host, emptyList()))
        .addInterceptor(authInterceptor)
        .rateLimit(2)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")

    // ================= Popular ===================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/obras/top10/views?periodo=total", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoResponse<List<MangoMangaDto>>>()

        val mangas = result.items.map { it.toSManga(cdnUrl) }

        return MangasPage(mangas, result.pagination?.hasNextPage ?: false)
    }

    // ================= Latest ===================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/capitulos/recentes?pagina=$page&limite=24", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoResponse<List<MangoMangaDto>>>()

        val mangas = result.items.map { it.toSManga(cdnUrl) }

        return MangasPage(mangas, result.pagination?.hasNextPage ?: false)
    }

    // ================= Search ===================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/obras".toHttpUrl().newBuilder()
            .addQueryParameter("busca", query)
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "20")

        filters.filterIsInstance<UrlQueryFilter>()
            .forEach { it.addQueryParameter(url) }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        StatusFilter(),
        FormatFilter(),
        TagFilter(),
    )

    // ================= Details ===================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val rawId = manga.url.substringAfterLast("/")
        val id = rawId.substringBefore("-")
        return GET("$baseUrl/api/obras/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoResponse<MangoMangaDto>>()
        val dto = result.items

        return dto.toSManga(cdnUrl)
    }

    // ================= Chapters ===================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoResponse<MangoMangaDto>>()
        val mangaDto = result.items

        return mangaDto.capitulos
            ?.map { it.toSChapter() }
            ?.sortedByDescending { it.chapter_number }
            ?: emptyList()
    }

    // ================= Pages ===================
    override fun pageListRequest(chapter: SChapter): Request {
        val apiUrl = baseUrl + "/api" + chapter.url.replace("/capitulo/", "/capitulos/")
            .replace("/obra/", "/obras/")
        return GET(apiUrl, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val encrypted = response.body.string()
        val decrypted = DecryptMango.decrypt(encrypted)

        val result = decrypted.parseAs<MangoPageResponse>()

        val capitulo = result.capitulo ?: return emptyList()

        return capitulo.paginas.mapIndexed { index, pageDto ->
            Page(index, imageUrl = pageDto.url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ========================= Preferences ================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val warning = "⚠️ Os dados inseridos nesta seção serão usados somente para realizar o login na fonte."
        val message = "Insira %s para prosseguir com o acesso aos recursos disponíveis na fonte."

        EditTextPreference(screen.context).apply {
            key = PREF_EMAIL
            title = "E-mail"
            summary = "E-mail de acesso"
            dialogMessage = buildString {
                appendLine(message.format("seu e-mail"))
                append("\n$warning")
            }
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().remove(PREF_TOKEN).apply()
                val password = preferences.getString(PREF_PASSWORD, "") ?: ""
                checkLogin(newValue as String, password)
                true
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Senha"
            summary = "Senha de acesso"
            dialogMessage = buildString {
                appendLine(message.format("sua senha"))
                append("\n$warning")
            }
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().remove(PREF_TOKEN).apply()
                val email = preferences.getString(PREF_EMAIL, "") ?: ""
                checkLogin(email, newValue as String)
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_EMAIL = "pref_email"
        private const val PREF_PASSWORD = "pref_password"
        private const val PREF_TOKEN = "pref_token"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
