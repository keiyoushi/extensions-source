package eu.kanade.tachiyomi.extension.pt.kuromangas

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
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
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

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/chapters/recent".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("days", "30")
            .build()

        val result = client.get(url, Fetch.XHR).parseAs<LatestResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        filters.firstInstanceOrNull<SortFilter>()?.let { filter ->
            url.addQueryParameter("sort", filter.selectedSort)
            url.addQueryParameter("order", filter.selectedOrder)
        } ?: run {
            url.addQueryParameter("sort", "view_count")
            url.addQueryParameter("order", "DESC")
        }

        val result = client.get(url.build(), Fetch.XHR).parseAs<MangaListResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

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
                client.get(mangaUrl, Fetch.XHR)
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

    private suspend fun fetchChapterPage(mangaUrl: String, page: Int): ChaptersResponse {
        val url = "$mangaUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("limit", CHAPTERS_LIMIT.toString())
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, Fetch.XHR).parseAs<ChaptersResponse>()
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga) = manga.url.substringAfterLast("/").let { mangaId ->
        client.get("$apiUrl/mangas/$mangaId/similar", Fetch.XHR)
            .parseAs<SimilarResponse>().data.map { it.toSManga(cdnUrl) }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("/")
        val chapterResponse = client.get(
            "$apiUrl/chapters/$chapterId",
            Fetch.XHR,
        ).parseAs<ChapterPagesResponse>()
        return chapterResponse.pages.mapIndexed { index, pageUrl ->
            val imageUrl = cdnUrl.toHttpUrl().resolve(pageUrl.replaceFirst(UPLOADS_PREFIX, "/"))!!
            Page(index, imageUrl = imageUrl.toString())
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.substringAfterLast("/")
        return "$baseUrl/manga/$mangaId"
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read" + chapter.url.removePrefix("/chapter")

    @Synchronized
    private fun checkLogin(): Boolean {
        if (hasSession()) return true
        return login()
    }

    // The site rejects requests carrying a session without its matching token.
    private fun hasSession(): Boolean = client.getCookie(baseUrl, SESSION_COOKIE) != null && client.getCookie(baseUrl, TOKEN_COOKIE) != null

    @Synchronized
    private fun login(): Boolean {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            return false
        }

        val requestBody = LoginBody(email, password).toJsonRequestBody()
        val request = POST("$apiUrl/auth/login", headers, requestBody).newBuilder().fetch(Fetch.XHR).build()
        network.client.newCall(request).execute().close()

        return hasSession()
    }

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
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
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
        val UPLOADS_PREFIX = Regex("^/uploads/")
    }
}

internal fun OkHttpClient.getCookie(baseUrl: String, cookie: String): String? = cookieJar.loadForRequest(baseUrl.toHttpUrl()).firstOrNull { it.name == cookie }?.value?.takeUnless { it.isEmpty() }
