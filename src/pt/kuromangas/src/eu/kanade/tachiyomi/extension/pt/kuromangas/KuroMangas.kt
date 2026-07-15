package eu.kanade.tachiyomi.extension.pt.kuromangas

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class KuroMangas :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val apiUrl = "$baseUrl/api"

    private val cdnUrl = "https://cdn.kuromangas.com"

    private val decryptor = KuroMangasDecryptor(baseUrl, network.client)

    override val client by lazy {

        network.client.newBuilder()
            .apply {
                addInterceptor { chain ->
                    checkLogin() ?: throw IOException(LOGIN_REQUIRED_MESSAGE)
                    return@addInterceptor chain.proceed(chain.request())
                }

                addInterceptor(decryptor.vSecureInterceptor())
            }
            .rateLimit(2)
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "application/json, text/plain, */*")
        .add("Accept-Language", "pt-BR,pt;q=0.8,en-US;q=0.5,en;q=0.3")
        .add("Referer", "$baseUrl/catalogo")
        .add("Sec-Fetch-Dest", "empty")
        .add("Sec-Fetch-Mode", "cors")
        .add("Sec-Fetch-Site", "same-origin")

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    // ============================= Popular ================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("sort", "view_count")
            .addQueryParameter("order", "DESC")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    // ============================= Latest =================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/chapters/recent".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .addQueryParameter("days", "30")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    // ============================= Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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
            url.addQueryParameter("sort", "created_at")
            url.addQueryParameter("order", "DESC")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.data.map { it.toSManga(cdnUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage())
    }

    // ============================= Details ================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/mangas/$mangaId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailsResponse>()
        return result.manga.toSManga(cdnUrl)
    }

    // ============================= Chapters ===============================

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/mangas/$mangaId", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailsResponse>()
        val mangaId = result.manga.id
        return result.chapters
            .map { it.toSChapter(mangaId, dateFormat) }
            .sortedByDescending { it.chapter_number }
    }

    // ============================= Pages ==================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterResponse = response.parseAs<ChapterPagesResponse>()
        return chapterResponse.pages.mapIndexed { index, pageUrl ->
            val fixedUrl = pageUrl.replaceFirst("^/uploads/".toRegex(), "/")
            val imageUrl = if (fixedUrl.startsWith("http")) fixedUrl else "$cdnUrl$fixedUrl"
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, newHeaders)
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

    private fun checkLogin(): Boolean? {
        client.getCookie(baseUrl, "kuro_session")?.also { return true }

        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            return null
        }
        login(email, password)

        return client.getCookie(baseUrl, "kuro_session").let { true }
    }

    // Implicit set-cookie: kuro_session + kuro_csrf
    private fun login(email: String, password: String) {
        val payload = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = POST("$apiUrl/auth/login", headers, requestBody)
        val response = network.client.newCall(request).execute()
    }

    // ============================= Preferences ============================

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

    override fun getFilterList() = getFilters()

    companion object {
        private const val PAGE_LIMIT = 24
        private const val API_HOST = "beta.kuromangas.com"
        private const val PREF_EMAIL = "kuromangas_email"
        private const val PREF_PASSWORD = "kuromangas_password"
        private const val LOGIN_REQUIRED_MESSAGE = "Faça login no WebView ou insira email e senha nas configurações e tente novamente."
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

fun OkHttpClient.getCookies(baseUrl: String) = cookieJar.loadForRequest(baseUrl.toHttpUrl())

fun OkHttpClient.getCookie(baseUrl: String, cookie: String): String? = getCookies(baseUrl).firstOrNull { it.name == cookie }?.value?.takeUnless { it.isEmpty() }
