package eu.kanade.tachiyomi.extension.pt.azuretoons

import android.content.SharedPreferences
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Azuretoons :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl get() = "$baseUrl/api"

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0L
    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authIntercept)
        .rateLimit(2)

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

    private fun clearTokenAndRefresh(): String? {
        cachedToken = null
        tokenExpiryTime = 0L
        return getValidToken()
    }

    private fun getValidToken(): String? {
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < tokenExpiryTime) return cachedToken
        return fetchNewToken()
    }

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

    protected open fun loginAndGetToken(email: String, password: String): String? {
        try {
            val body = AzuretoonsLoginRequestDto(
                identifier = email.trim(),
                password = password,
            ).toJsonRequestBody()
            val loginHeaders = headers.newBuilder().set("Accept", "application/json").build()
            val response = network.client.newCall(
                POST("$apiUrl/auth/login", loginHeaders, body),
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

    override fun Headers.Builder.configureHeaders() = set("Accept", ACCEPT)
        .set("Accept-Language", ACCEPT_LANGUAGE)
        .set("Pragma", PRAGMA)

    // ============================== Popular (Browse) =======================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val dto = client.get("$apiUrl/obras").parseAs<List<AzuretoonsMangaDto>>()
        val mangas = dto.sortedByDescending { it.viewCount }.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================= Latest ===================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val dto = client.get("$apiUrl/obras").parseAs<List<AzuretoonsMangaDto>>()
        val mangas = dto.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = false)
    }

    // =============================== Search (na mão: filtra por título) =====
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val dto = client.get("$apiUrl/obras").parseAs<List<AzuretoonsMangaDto>>()
        val mangas = dto
            .map { it.toSManga() }
            .let { list ->
                if (query.isNotEmpty()) {
                    list.filter { it.title.contains(query, ignoreCase = true) }
                } else {
                    list
                }
            }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================ Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfter("/obra/").substringBefore("/")
        val dto = client.get("$apiUrl/obras/slug/$slug").parseAs<AzuretoonsMangaDto>()
        val chapterList = dto.chapters
            .map { it.toSChapter(dto.slug) }
            .distinctBy { it.url }
            .sortedByDescending { it.chapter_number }
        return SMangaUpdate(dto.toSManga(), chapterList)
    }

    // =============================== Pages =================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfter("/capitulo/")
        val slug = chapter.url.substringAfter("/obra/").substringBefore("/")
        return client.get("$apiUrl/chapters/read/$slug/$chapterId")
            .parseAs<AzuretoonsChapterDetailDto>()
            .toPageList()
    }

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
