package eu.kanade.tachiyomi.extension.pt.toonbr

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
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient

@Source
abstract class ToonBr :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = apply {
        val token = getToken()
        if (token.isNotEmpty()) {
            addCookie({ API_HOST }, "token" to token)
        }
        rateLimit(2)
    }

    private val apiUrl = "https://api.toonbr.com"
    private val cdnUrl = "https://cdn2.toonbr.com"

    private fun getToken(): String {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            return ""
        }
        return runCatching { login(email, password) }.getOrDefault("")
    }

    // ===== Popular =====
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangaList = client.get("$apiUrl/api/manga/popular?limit=$PAGE_LIMIT").parseAs<List<MangaDto>>()
        val mangas = mangaList.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ===== Latest =====
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangaList = client.get("$apiUrl/api/manga/latest?limit=$PAGE_LIMIT").parseAs<List<MangaDto>>()
        val mangas = mangaList.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ===== Search =====
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = buildString {
            append("$apiUrl/api/manga?page=$page&limit=$PAGE_LIMIT")
            if (query.isNotBlank()) {
                append("&search=$query")
            }
            filters.firstInstanceOrNull<CategoryFilter>()?.selected?.let { categoryId ->
                append("&categoryId=$categoryId")
            }
        }
        val result = client.get(url).parseAs<MangaListResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, false)
    }

    // ===== Manga Details / Chapters =====
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/")
        val mangaDto = client.get("$apiUrl/api/manga/$slug").parseAs<MangaDto>()

        val chapterList = mangaDto.chapters
            ?.map { it.toSChapter() }
            ?.sortedByDescending { it.chapter_number }
            ?: emptyList()

        return SMangaUpdate(mangaDto.toSManga(cdnUrl), chapterList)
    }

    // ===== Pages =====
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("/")
        val chapterDto = client.get("$apiUrl/api/chapter/$chapterId").parseAs<ChapterDto>()
        return chapterDto.pages
            ?.mapIndexedNotNull { index, page ->
                page.imageUrl?.let { Page(index, imageUrl = "$cdnUrl$it") }
            }
            ?: emptyList()
    }

    // ====== Utils ======

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterId = chapter.url.substringAfterLast("/")
        return "$baseUrl/read/$chapterId"
    }

    // ===== Authentication =====
    // runs while the client is being built, so it has to use the base client synchronously
    private fun login(email: String, password: String): String {
        val requestBody = LoginRequest(email, password).toJsonRequestBody()
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

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private const val PAGE_LIMIT = 150
        private const val API_HOST = "api.toonbr.com"
        private const val PREF_EMAIL = "toonbr_email"
        private const val PREF_PASSWORD = "toonbr_password"
    }
}
