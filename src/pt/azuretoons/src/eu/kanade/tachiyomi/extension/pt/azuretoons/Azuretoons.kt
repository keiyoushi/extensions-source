package eu.kanade.tachiyomi.extension.pt.azuretoons

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.jvm.Synchronized

class Azuretoons :
    HttpSource(),
    ConfigurableSource {

    override val name = "Azuretoons"
    override val baseUrl = "https://azuretoons.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    private val apiUrl = "https://azuretoons.com/api"

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0L
    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .addInterceptor(::authIntercept)
        .build()

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header("Authorization") != null) return chain.proceed(request)
        val token = getValidToken() ?: return chain.proceed(request)
        val response = chain.proceed(request.withAuth(token))
        if (response.code != 401) return response
        response.close()
        val newToken = clearTokenAndRefresh()
        return chain.proceed(request.withAuth(newToken.orEmpty()))
    }

    private fun Request.withAuth(token: String): Request = if (token.isNotEmpty()) {
        newBuilder().header("Authorization", "Bearer $token").build()
    } else {
        this
    }

    @Synchronized
    private fun clearTokenAndRefresh(): String? {
        cachedToken = null
        tokenExpiryTime = 0L
        return getValidToken()
    }

    @Synchronized
    private fun getValidToken(): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTime) return cachedToken
        return fetchNewToken()
    }

    @Synchronized
    private fun fetchNewToken(): String? {
        return try {
            val email = preferences.getString(EMAIL_PREF, "")
            val password = preferences.getString(PASSWORD_PREF, "")
            if (email.isNullOrEmpty() || password.isNullOrEmpty()) return null
            return loginAndGetToken(email, password)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    protected open fun loginAndGetToken(email: String, password: String): String? {
        try {
            val body = AzuretoonsLoginRequestDto(
                identifier = email.trim(),
                password = password,
            ).toJsonString().toRequestBody("application/json".toMediaType())
            val headers = headersBuilder().set("Accept", "application/json").build()
            val response = network.cloudflareClient.newCall(
                POST("$apiUrl/auth/login", headers, body),
            ).execute()

            if (!response.isSuccessful) {
                response.close()
                return null
            }
            val auth = response.parseAs<AzuretoonsLoginResponseDto>()
            val token = auth.accessToken
            val expiresIn = auth.expiresIn * 1000
            cachedToken = token
            tokenExpiryTime = System.currentTimeMillis() + expiresIn
            return token
        } catch (e: Exception) {
            return null
        }
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", ACCEPT)
        .set("Accept-Language", ACCEPT_LANGUAGE)
        .set("Pragma", PRAGMA)
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    // ============================== Popular (Browse) =======================
    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/obras", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<List<AzuretoonsMangaDto>>()
        val mangas = dto.sortedByDescending { it.viewCount }.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/obras", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<List<AzuretoonsMangaDto>>()
        val mangas = dto.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = false)
    }

    // =============================== Search (na mão: filtra por título) =====
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .fragment(query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment
        val dto = response.parseAs<List<AzuretoonsMangaDto>>()
        val mangas = dto
            .map { it.toSManga() }
            .let { list ->
                if (!query.isNullOrEmpty()) {
                    list.filter { it.title.lowercase().contains(query) }
                } else {
                    list
                }
            }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================ Manga Details ============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/obra/").substringBefore("/")
        return GET("$apiUrl/obras/slug/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<AzuretoonsMangaDto>()
        return dto.toSManga()
    }

    // ============================== Chapters ================================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val manga = response.parseAs<AzuretoonsMangaDto>()
        return manga.chapters
            .map { it.toSChapter(manga.slug) }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
    }

    // =============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/capitulo/")
        val slug = chapter.url.substringAfter("/obra/").substringBefore("/")
        return GET("$apiUrl/chapters/read/$slug/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<AzuretoonsChapterDetailDto>()
        return dto.toPageList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = EMAIL_PREF
            title = "Email"
            summary = "Email para login automático"
            setDefaultValue("")
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "Senha"
            summary = "Senha para login automático"
            setDefaultValue("")
        }.also(screen::addPreference)
    }

    companion object {
        private const val ACCEPT = "*/*"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7"
        private const val PRAGMA = "no-cache"
        private const val EMAIL_PREF = "email"
        private const val PASSWORD_PREF = "password"
    }
}
