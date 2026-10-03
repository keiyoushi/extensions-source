package eu.kanade.tachiyomi.multisrc.mangotheme

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.math.abs

abstract class MangoTheme :
    KeiSource(),
    ConfigurableSource {

    protected abstract val encryptionKey: String

    protected open val apiUrl: String
        get() = "$baseUrl/api"

    protected open val cdnUrl: String
        get() = baseUrl

    protected open val webMangaPathSegment = "obra"

    protected open val webChapterPathSegment = "capitulo"

    protected open val webUrlSalt: String? = null

    protected open val webUrlWindowMillis = 7_200_000L

    protected open fun buildTimedWebMangaReference(mangaId: String, hash: String): String = "$mangaId-$hash"

    protected open val requiresLogin = false

    protected open val latestPageSize = 24

    protected open val searchPageSize = 20

    protected open val emailPreferenceKey = PREF_EMAIL

    protected open val passwordPreferenceKey = PREF_PASSWORD

    protected open val tokenPreferenceKey = PREF_TOKEN

    protected open val loginRequiredMessage = "Faça o login nas configurações"

    protected open val loginSuccessMessage = "Login realizado com sucesso"

    protected open val loginWarningMessage =
        "⚠️ Os dados inseridos nesta seção serão usados somente para realizar o login na fonte."

    protected open val loginDialogMessageFormat =
        "Insira %s para prosseguir com o acesso aos recursos disponíveis na fonte."

    protected open val emailPreferenceTitle = "E-mail"

    protected open val emailPreferenceSummary = "E-mail de acesso"

    protected open val passwordPreferenceTitle = "Senha"

    protected open val passwordPreferenceSummary = "Senha de acesso"

    protected abstract fun getFormatFilterOptions(): Array<Pair<String, String>>

    protected abstract fun getStatusFilterOptions(): Array<Pair<String, String>>

    protected abstract fun getTagFilterOptions(): List<MangoThemeTagFilterOption>

    protected val preferences by getPreferencesLazy()

    override val supportsLatest = true

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()

        if (!requiresLogin || !request.requiresAuthorization()) {
            return@Interceptor chain.proceed(request)
        }

        val token = getToken()
        val authenticatedRequest = token.takeIf { it.isNotEmpty() }
            ?.let { request.newBuilder().header("Authorization", "Bearer $it").build() }
            ?: request

        val response = chain.proceed(authenticatedRequest)
        if (response.code != 401) {
            return@Interceptor response
        }

        response.close()
        clearToken()

        val refreshedToken = getToken()
        if (refreshedToken.isNotEmpty()) {
            val retriedResponse = chain.proceed(
                request.newBuilder()
                    .header("Authorization", "Bearer $refreshedToken")
                    .build(),
            )

            if (retriedResponse.code != 401) {
                return@Interceptor retriedResponse
            }
            retriedResponse.close()
        }

        throw IOException(loginRequiredMessage)
    }

    private val decryptInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (!response.headers[ENCRYPTED_HEADER].toBoolean()) {
            return@Interceptor response
        }

        val decryptedBody = MangoThemeDecrypt.decrypt(response.body.string(), encryptionKey)

        response.newBuilder()
            .body(decryptedBody.toResponseBody(JSON_MEDIA_TYPE))
            .build()
    }

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(authInterceptor)
        .addInterceptor(decryptInterceptor)
        .rateLimit(2)

    override fun Headers.Builder.configureHeaders() = addMangoThemeHeaders()

    protected fun Headers.Builder.addMangoThemeHeaders(): Headers.Builder = set("Accept-Language", "$lang, en-US;q=0.8, en;q=0.7")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/obras/top10/views?periodo=total")
            .parseAs<MangoThemeResponse<List<MangoThemeMangaDto>>>()
        return MangasPage(result.items.map { it.toSManga(cdnUrl) }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(client.get("$apiUrl/capitulos/recentes?pagina=$page&limite=$latestPageSize"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", searchPageSize.toString())

        query.takeIf { it.isNotBlank() }?.let { url.addQueryParameter("busca", it) }

        filters.filterIsInstance<MangoThemeUrlQueryFilter>()
            .forEach { it.addQueryParameter(url) }

        return parseMangasPage(client.get(url.build()))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val result = response.parseAs<MangoThemeResponse<List<MangoThemeMangaDto>>>()
        return MangasPage(
            mangas = result.items.map { it.toSManga(cdnUrl) },
            hasNextPage = result.pagination?.hasNextPage == true,
        )
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            StatusFilter(getStatusFilterOptions()),
            FormatFilter(getFormatFilterOptions()),
            MinChaptersFilter(),
        )

        getTagFilterOptions()
            .takeIf { it.isNotEmpty() }
            ?.let { filters += TagFilter(it) }

        return FilterList(filters)
    }

    // details and chapters come from the same endpoint
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val storedSlug = manga.url.extractStoredSlug().takeIf { it.isNotEmpty() }
        val dto = client.get("$apiUrl/obras/${manga.url.extractMangaId()}")
            .parseAs<MangoThemeResponse<MangoThemeMangaDto>>()
            .items

        val fallbackSlug = dto.slug ?: storedSlug
        val chapterList = dto.chapters
            .map { it.toSChapter(fallbackSlug) }
            .sortedByDescending { it.chapter_number }

        return SMangaUpdate(dto.toSManga(cdnUrl, storedSlug), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.url.extractChapterMangaId()
        val chapterNumber = chapter.url.extractChapterNumber()

        val pages = client.get("$apiUrl/obras/$mangaId/capitulos/$chapterNumber")
            .parseAs<MangoThemeResponse<MangoThemePageChapterDto>>()
            .items
            .pages
            .sortedBy { it.number }
            .mapNotNull { page ->
                page.url
                    ?.takeIf { it.isNotBlank() }
                    ?.let { it.toAbsolutePageUrl(cdnUrl) }
            }
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }

        if (pages.isEmpty()) {
            throw IOException("No valid page URLs found in chapter response")
        }

        return pages
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/$webMangaPathSegment/${manga.url.toWebMangaReference()}"

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaReference = chapter.url.extractChapterWebMangaReference()
        val chapterNumber = chapter.url.extractChapterNumber()
        return "$baseUrl/$webMangaPathSegment/$mangaReference/$webChapterPathSegment/$chapterNumber"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        if (!requiresLogin) {
            return
        }

        val emailDialogMessage = buildString {
            appendLine(loginDialogMessageFormat.format("seu e-mail"))
            append("\n$loginWarningMessage")
        }
        val passwordDialogMessage = buildString {
            appendLine(loginDialogMessageFormat.format("sua senha"))
            append("\n$loginWarningMessage")
        }

        EditTextPreference(screen.context).apply {
            key = emailPreferenceKey
            title = emailPreferenceTitle
            summary = emailPreferenceSummary
            dialogMessage = emailDialogMessage
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                clearToken()
                val password = preferences.getString(passwordPreferenceKey, "").orEmpty()
                checkLogin(newValue as String, password)
                true
            }
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = passwordPreferenceKey
            title = passwordPreferenceTitle
            summary = passwordPreferenceSummary
            dialogMessage = passwordDialogMessage
            setDefaultValue("")
            setOnPreferenceChangeListener { _, newValue ->
                clearToken()
                val email = preferences.getString(emailPreferenceKey, "").orEmpty()
                checkLogin(email, newValue as String)
                true
            }
        }.let(screen::addPreference)
    }

    @Synchronized
    protected open fun getToken(): String {
        val email = preferences.getString(emailPreferenceKey, "").orEmpty()
        val password = preferences.getString(passwordPreferenceKey, "").orEmpty()

        if (email.isEmpty() || password.isEmpty()) {
            return ""
        }

        val savedToken = preferences.getString(tokenPreferenceKey, "").orEmpty()
        if (savedToken.isNotEmpty()) {
            return savedToken
        }

        return login(email, password)
    }

    protected open fun login(email: String, password: String): String = runCatching {
        val body = MangoThemeAuthRequestDto(
            email = email,
            password = password,
        ).toJsonString().toRequestBody(JSON_MEDIA_TYPE)

        val request = POST("$apiUrl/auth/login", headers, body)
        network.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                ""
            } else {
                val loginResponse = response.parseAs<MangoThemeLoginResponseDto>()
                loginResponse.token
                    ?.takeIf { it.isNotBlank() }
                    ?.also { preferences.edit().putString(tokenPreferenceKey, it).apply() }
                    .orEmpty()
            }
        }
    }.getOrDefault("")

    protected open fun clearToken() {
        preferences.edit().remove(tokenPreferenceKey).apply()
    }

    private fun checkLogin(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            return
        }

        Thread {
            val token = login(email, password)
            if (token.isNotEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        Injekt.get<Application>(),
                        loginSuccessMessage,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }.start()
    }

    private fun Request.requiresAuthorization(): Boolean {
        val host = url.host
        val path = url.encodedPath
        return host == apiUrl.toHttpUrl().host &&
            path.startsWith("/api/") &&
            path != "/api/auth/login"
    }

    private fun String.extractMangaId(): String = substringAfterLast("/")
        .substringBefore("?")
        .substringBefore("#")
        .substringBefore("-")

    private fun String.extractChapterMangaId(): String = substringAfter("/obra/")
        .substringBefore("/")

    private fun String.extractChapterNumber(): String = substringAfterLast("/")
        .substringBefore("?")
        .substringBefore("#")

    private fun String.toAbsolutePageUrl(baseUrl: String): String = takeIf { it.toHttpUrlOrNull() != null } ?: "$baseUrl/${trimStart('/')}"

    private fun String.toWebMangaReference(): String {
        val storedSlug = extractStoredSlug()
        return if (webUrlSalt != null) {
            buildWebMangaSlug(extractMangaId())
        } else {
            storedSlug.ifEmpty { extractMangaId() }
        }
    }

    private fun String.extractChapterWebMangaReference(): String {
        val storedSlug = extractStoredSlug()
        return if (webUrlSalt != null) {
            buildWebMangaSlug(extractChapterMangaId())
        } else {
            storedSlug.ifEmpty { extractChapterMangaId() }
        }
    }

    private fun String.extractStoredSlug(): String = substringAfter("?slug=", "")
        .substringBefore("&")

    private fun buildWebMangaSlug(mangaId: String): String = webUrlSalt
        ?.let { salt ->
            buildTimedWebMangaReference(
                mangaId = mangaId,
                hash = createTimedWebUrlHash(mangaId, salt),
            )
        }
        ?: mangaId

    // Reproduces the 2-hour public web slug used by MangoTheme frontends.
    private fun createTimedWebUrlHash(mangaId: String, salt: String): String {
        val timeWindow = System.currentTimeMillis() / webUrlWindowMillis
        val payload = "$mangaId-$timeWindow-$salt"
        var hash = 0

        payload.forEach { char ->
            hash = (hash shl 5) - hash + char.code
        }

        return abs(hash.toLong()).toString(36)
    }

    companion object {
        private const val PREF_EMAIL = "pref_email"
        private const val PREF_PASSWORD = "pref_password"
        private const val PREF_TOKEN = "pref_token"
        private const val ENCRYPTED_HEADER = "x-encrypted"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
