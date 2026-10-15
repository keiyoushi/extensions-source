package eu.kanade.tachiyomi.extension.pt.kuromangas

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Source
abstract class KuroMangas :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl get() = "$baseUrl/api"

    private val cdnUrl = "https://cdn.kuromangas.com"

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            if (!checkLogin()) throw IOException(LOGIN_REQUIRED_MESSAGE)
            return@addInterceptor chain.proceed(chain.request())
        }

        addInterceptor(
            KuroMangasDecryptor(
                baseUrl,
                network.client,
                ::login,
            ).vSecureInterceptor(),
        )
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set("Accept", "application/json, text/plain, */*")
        set("Accept-Language", "pt-BR,pt;q=0.8,en-US;q=0.5,en;q=0.3")
        set("Referer", "$baseUrl/catalogo")
        set("Sec-Fetch-Dest", "empty")
        set("Sec-Fetch-Mode", "cors")
        set("Sec-Fetch-Site", "same-origin")
    }

    // ============================= Popular ================================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    // ============================= Latest =================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/chapters/recent".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("days", "30")
            .build()

        val result = client.get(url).parseAs<LatestResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    // ============================= Search =================================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        filters.filterIsInstance<SortFilter>().firstOrNull()?.let { filter ->
            url.addQueryParameter("sort", filter.selectedSort)
            url.addQueryParameter("order", filter.selectedOrder)
        } ?: run {
            url.addQueryParameter("sort", "view_count")
            url.addQueryParameter("order", "DESC")
        }

        val result = client.get(url.build()).parseAs<MangaListResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    // =========================== MangaUpdate ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = coroutineScope {
        val mangaId = manga.url.substringAfterLast("/").toInt()
        val mangaUrl = "$apiUrl/mangas/$mangaId"

        val updatedManga = async {
            if (fetchDetails) {
                client.get(mangaUrl)
                    .parseAs<MangaDetailsResponse>()
                    .manga.toSManga(cdnUrl)
            } else {
                manga
            }
        }

        val updatedChapters = async {
            if (fetchChapters) {
                val firstPage = fetchChapterPage(mangaUrl, 1)
                val extra = (2..(firstPage.pagination.totalPages ?: 0)).map { page ->
                    async { fetchChapterPage(mangaUrl, page).chapters }
                }
                (firstPage.chapters + extra.awaitAll().flatten())
                    .map { it.toSChapter(mangaId) }
                    .sortedByDescending { it.chapter_number }
            } else {
                chapters
            }
        }

        SMangaUpdate(updatedManga.await(), updatedChapters.await())
    }

    private suspend fun fetchChapterPage(mangaUrl: String, page: Int) = client.get(
        "$mangaUrl/chapters?limit=$CHAPTERS_LIMIT&page=$page",
    ).parseAs<ChaptersResponse>()

    // ============================ Related =================================

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga) = manga.url.substringAfterLast("/").let { mangaId ->
        client.get("$apiUrl/mangas/$mangaId/similar")
            .parseAs<SimilarResponse>().data.map { it.toSManga(cdnUrl) }
    }

    // ============================= Pages ==================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("/")
        val chapterResponse = client.get(
            "$apiUrl/chapters/$chapterId",
        ).parseAs<ChapterPagesResponse>()
        return chapterResponse.pages.mapIndexed { index, pageUrl ->
            val fixedUrl = pageUrl.replaceFirst("^/uploads/".toRegex(), "/")
            val imageUrl = if (fixedUrl.startsWith("http")) fixedUrl else "$cdnUrl$fixedUrl"
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================= Utils ==================================

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast("/")
        return "$baseUrl/manga/$mangaId"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        // chapter.url format: /chapter/{mangaId}/{chapterId}
        val parts = chapter.url.removePrefix("/chapter/").split("/")
        val mangaId = parts.getOrNull(0) ?: ""
        val chapterId = parts.getOrNull(1) ?: ""
        return "$baseUrl/reader/$mangaId/$chapterId"
    }

    // ============================= Auth ===================================

    private fun checkLogin(): Boolean {
        if (hasSession()) return true
        return login()
    }

    // The site rejects requests carrying a session without its matching token.
    private fun hasSession(): Boolean = client.getCookie(baseUrl, SESSION_COOKIE) != null && client.getCookie(baseUrl, TOKEN_COOKIE) != null

    // Implicit set-cookie: kuro_session + _kn
    private fun login(): Boolean {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            return false
        }

        val payload = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = POST("$apiUrl/auth/login", headers, requestBody)
        network.client.newCall(request).execute().close()

        return hasSession()
    }

    // ============================= Preferences ============================

    private val preferences = getPreferences()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val warning = "⚠️ Os dados inseridos nesta seção serão usados somente para realizar o login na fonte"
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

    private companion object {
        const val PAGE_LIMIT = 24
        const val CHAPTERS_LIMIT = 60
        const val PREF_EMAIL = "kuromangas_email"
        const val PREF_PASSWORD = "kuromangas_password"
        const val SESSION_COOKIE = "kuro_session"
        const val LOGIN_REQUIRED_MESSAGE = "Faça login no WebView ou insira email e senha nas configurações e tente novamente."
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

fun OkHttpClient.getCookies(baseUrl: String) = cookieJar.loadForRequest(baseUrl.toHttpUrl())

fun OkHttpClient.getCookie(baseUrl: String, cookie: String): String? = getCookies(baseUrl).firstOrNull { it.name == cookie }?.value?.takeUnless { it.isEmpty() }
