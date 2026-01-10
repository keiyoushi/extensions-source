package eu.kanade.tachiyomi.extension.pt.toonbr

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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ToonBr : HttpSource(), ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override val name = "ToonBr"
    override val baseUrl = "https://beta.toonbr.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override val client by lazy {
        val token = getToken()
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .apply {
                if (token.isNotEmpty()) {
                    addInterceptor(CookieInterceptor(API_HOST, "token" to token))
                }
            }
            .build()
    }

    private val apiUrl = "https://api.toonbr.com"
    private val cdnUrl = "https://cdn2.toonbr.com"

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
    }

    private fun getToken(): String {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            return ""
        }
        return runCatching { login(email, password) }.getOrDefault("")
    }

    // ===== Popular =====
    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/api/manga/popular?limit=$PAGE_LIMIT", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaList = response.parseAs<List<MangaDto>>()
        val mangas = mangaList.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ===== Latest =====
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/api/manga/latest?limit=$PAGE_LIMIT", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangaList = response.parseAs<List<MangaDto>>()
        val mangas = mangaList.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ===== Search =====
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = buildString {
            append("$apiUrl/api/manga?page=$page&limit=$PAGE_LIMIT")
            if (query.isNotBlank()) {
                append("&search=$query")
            }
            filters.filterIsInstance<CategoryFilter>().firstOrNull()?.selected?.let { categoryId ->
                append("&categoryId=$categoryId")
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ===== Manga Details =====
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/api/manga/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaDto>().toSManga(cdnUrl)
    }

    // ===== Chapters =====
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/api/manga/$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaDto = response.parseAs<MangaDto>()
        return mangaDto.chapters
            ?.map { it.toSChapter(dateFormat) }
            ?.sortedByDescending { it.chapter_number }
            ?: emptyList()
    }

    // ===== Pages =====
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/api/chapter/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterDto = response.parseAs<ChapterDto>()
        return chapterDto.pages
            ?.mapIndexedNotNull { index, page ->
                page.imageUrl?.let { Page(index, imageUrl = "$cdnUrl$it") }
            }
            ?: emptyList()
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used")
    }

    // ====== Utils ======

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterId = chapter.url.substringAfterLast("/")
        return "$baseUrl/read/$chapterId"
    }

    // ===== Authentication =====
    private fun login(email: String, password: String): String {
        val payload = """{ "email": "$email", "password": "$password" }"""
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = POST("$apiUrl/api/auth/login", headers, requestBody)
        val response = network.client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("Login failed: ${response.code}")
        }
        return response.parseAs<LoginResponse>().token
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val warning = "⚠️ Os dados inseridos nessa seção serão usados somente para realizar o login na fonte"
        val message = "Insira %s para prosseguir com o acesso aos recursos disponíveis na fonte"

        EditTextPreference(screen.context).apply {
            key = PREF_EMAIL
            title = "📧 Email"
            summary = "Email de acesso"
            dialogMessage = buildString {
                appendLine(message.format("seu email"))
                append("\n$warning")
            }
            setDefaultValue("")
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "🔑 Senha"
            summary = "Senha de acesso"
            dialogMessage = buildString {
                appendLine(message.format("sua senha"))
                append("\n$warning")
            }
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    override fun getFilterList() = getFilters()

    companion object {
        private const val PAGE_LIMIT = 150
        private const val API_HOST = "api.toonbr.com"
        private const val PREF_EMAIL = "toonbr_email"
        private const val PREF_PASSWORD = "toonbr_password"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
