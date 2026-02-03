package eu.kanade.tachiyomi.extension.pt.geasscomics

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.utils.tryParse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class GeassComics :
    HttpSource(),
    ConfigurableSource {

    override val name = "Geass Comics"

    override val baseUrl = "https://geasscomics.xyz"

    private val apiUrl = "$baseUrl/api"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val json: Json = Injekt.get<Json>()

    private val chapterDateMap = mutableMapOf<String, Long>()

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .addInterceptor { chain ->
                val request = chain.request()
                val token = getToken()
                val newRequest = if (token.isNotEmpty()) {
                    request.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    request
                }
                chain.proceed(newRequest)
            }
            .addInterceptor(GeassComicsImageInterceptor())
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "application/json, text/plain, */*")

    // ========================= Popular ====================================

    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/leaderboard?criteria=global&withHentai=${isAdultActive()}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val list = response.parseAs<List<MangaDto>>()
        return MangasPage(list.map { it.toSManga() }, false)
    }

    // ========================= Latest =====================================

    override fun latestUpdatesRequest(page: Int): Request {
        val limit = 20
        val offset = (page - 1) * limit
        return GET("$apiUrl/releases?offset=$offset&limit=$limit&withHentai=${isAdultActive()}", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<MangaListDto>()

        dto.items.forEach { manga ->
            manga.lastChapters.forEach { chapter ->
                val date = chapter.date?.let { parseDate(it) } ?: 0L
                if (date > 0L) {
                    chapterDateMap["${manga.id}-${chapter.chapterNumber}"] = date
                }
            }
        }

        return MangasPage(dto.items.map { it.toSManga() }, dto.pagination?.hasNextPage ?: false)
    }

    // ========================= Search =====================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val limit = 20
        val offset = (page - 1) * limit

        val urlBuilder = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("withHentai", isAdultActive().toString())

        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("search")
            urlBuilder.addQueryParameter("query", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state.filter { it.state }.forEach {
                        urlBuilder.addQueryParameter("genres", it.name)
                    }
                }

                is TagList -> {
                    filter.state.filter { it.state }.forEach {
                        urlBuilder.addQueryParameter("tags", it.name)
                    }
                }

                else -> {}
            }
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    // ========================= Details ====================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val apiPath = manga.url.replace("/obra/", "/manga/")
        return GET("$apiUrl$apiPath", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga()

    // ========================= Chapters ===================================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val apiPath = manga.url.replace("/obra/", "/manga/")
        return GET("$apiUrl$apiPath/chapter?offset=0&limit=10000", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ChapterListDto>()
        val mangaId = response.request.url.pathSegments[2]

        return result.items.map {
            SChapter.create().apply {
                name = it.title
                chapter_number = it.chapterNumber
                val num = it.chapterNumber.toString().removeSuffix(".0")
                url = "/obra/$mangaId/capitulo/$num"

                date_upload = chapterDateMap["$mangaId-${it.chapterNumber}"] ?: 0L
            }
        }
    }

    private fun parseDate(dateStr: String): Long = dateFormat.tryParse(dateStr)

    // ========================= Pages ======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""

        if (email.isEmpty() || password.isEmpty()) {
            val message = "Para acessar as imagens é necessário fazer o login nas configurações."
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(Injekt.get<Application>(), message, Toast.LENGTH_LONG).show()
            }
            throw Exception(message)
        }

        val newHeaders = headersBuilder()
            .add("x-mymangas-secure-panel-domain", "true")
            .build()

        val apiPath = chapter.url
            .replace("/obra/", "/manga/")
            .replace("/capitulo/", "/chapter/")

        return GET("$apiUrl$apiPath/images", newHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val urls = response.parseAs<List<String>>()
        return urls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Filters ====================================

    override fun getFilterList() = getFilters()

    // ========================= Auth =======================================

    private fun isAdultActive() = preferences.getBoolean(PREF_ADULT_KEY, false)

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
        val request = POST("$apiUrl/visitor/auth/login", headers, body)
        val response = network.client.newCall(request).execute()
        if (response.isSuccessful) {
            val authResponse = response.parseAs<AuthResponseDto>()
            preferences.edit().putString(PREF_TOKEN, authResponse.jwt.token).apply()
            authResponse.jwt.token
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

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ADULT_KEY
            title = "Exibir conteúdo adulto"
            summary = "Habilita a visualização de mangás Hentai nas listas."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_EMAIL = "pref_email"
        private const val PREF_PASSWORD = "pref_password"
        private const val PREF_TOKEN = "pref_token"
        private const val PREF_ADULT_KEY = "pref_adult_content"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
