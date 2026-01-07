package eu.kanade.tachiyomi.extension.pt.skkytoons

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SkkyToons : HttpSource(), ConfigurableSource {

    override val name = "SkkyToons"

    override val baseUrl = "https://www.skkyscan.fun"

    private val apiUrl = "https://api.skkyscan.fun"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override val client by lazy {
        val token = getToken()
        network.cloudflareClient.newBuilder()
            .rateLimit(3)
            .apply {
                if (token.isNotEmpty()) {
                    addInterceptor { chain ->
                        val request = chain.request()
                        val newRequest = request.newBuilder()
                            .header("Authorization", "Bearer $token")
                            .build()
                        chain.proceed(newRequest)
                    }
                }
            }
            .build()
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", "application/json, text/plain, */*")
        .add("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("Referer", "$baseUrl/")
        .add("Sec-Fetch-Dest", "empty")
        .add("Sec-Fetch-Mode", "cors")
        .add("Sec-Fetch-Site", "same-site")

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private var cachedGenres: List<GenreTagDto> = emptyList()
    private var cachedTags: List<GenreTagDto> = emptyList()
    private var fetchFiltersAttempts = 0
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun launchIO(block: () -> Unit) = scope.launch { block() }

    // ============================= Auth ===================================

    private fun getToken(): String {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            return ""
        }
        return runCatching { login(email, password) }.getOrDefault("")
    }

    private fun checkLogin() {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""
        if (email.isEmpty() || password.isEmpty()) {
            throw Exception(LOGIN_REQUIRED_MESSAGE)
        }
    }

    private fun login(email: String, password: String): String {
        val payload = json.encodeToString(LoginRequest(email, password))
        val requestBody = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = POST("$apiUrl/api/auth/login", headers, requestBody)
        val response = network.cloudflareClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("Login failed: ${response.code}")
        }
        val loginResponse = response.parseAs<ApiResponse<LoginResponseData>>()
        return loginResponse.data.accessToken
    }

    // ============================= Popular ================================

    override fun popularMangaRequest(page: Int): Request {
        checkLogin()
        val url = "$apiUrl/api/mangas/ranking".toHttpUrl().newBuilder()
            .addQueryParameter("period", "all")
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiResponse<List<MangaDto>>>()
        val mangas = result.data.map { it.toSManga(apiUrl) }
        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================= Latest =================================

    override fun latestUpdatesRequest(page: Int): Request {
        checkLogin()
        val url = "$apiUrl/api/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<ApiListResponse<MangaDto>>()
        val mangas = result.data.map { it.toSManga(apiUrl) }
        val hasNext = result.pagination?.hasNextPage() ?: false
        return MangasPage(mangas, hasNext)
    }

    // ============================= Search =================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        checkLogin()
        val url = "$apiUrl/api/mangas/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_LIMIT.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
        }

        var showNsfw = false

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    url.addQueryParameter("sort", filter.selected)
                    url.addQueryParameter("order", filter.order)
                }
                is StatusFilter -> {
                    filter.selected?.let { url.addQueryParameter("status", it) }
                }
                is NsfwFilter -> {
                    showNsfw = filter.state
                }
                is GenreFilter -> {
                    val selectedGenres = filter.state
                        .filterIsInstance<GenreCheckBox>()
                        .filter { it.state }
                        .map { it.id }
                    if (selectedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres", selectedGenres.joinToString(","))
                    }
                }
                is TagFilter -> {
                    val selectedTags = filter.state
                        .filterIsInstance<TagCheckBox>()
                        .filter { it.state }
                        .map { it.id }
                    if (selectedTags.isNotEmpty()) {
                        url.addQueryParameter("tags", selectedTags.joinToString(","))
                    }
                }
                else -> {}
            }
        }

        url.addQueryParameter("nsfw", showNsfw.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiListResponse<MangaDto>>()
        val mangas = result.data.map { it.toSManga(apiUrl) }
        val hasNext = result.pagination?.hasNextPage() ?: false
        return MangasPage(mangas, hasNext)
    }

    private fun fetchFilters() {
        if (cachedGenres.isNotEmpty() && cachedTags.isNotEmpty()) return
        if (fetchFiltersAttempts >= 3) return
        fetchFiltersAttempts++

        runCatching {
            val genresRequest = GET("$apiUrl/api/genres", headers)
            val genresResponse = client.newCall(genresRequest).execute()
            if (genresResponse.isSuccessful) {
                cachedGenres = genresResponse.parseAs<ApiResponse<List<GenreTagDto>>>().data
            }

            val tagsRequest = GET("$apiUrl/api/tags", headers)
            val tagsResponse = client.newCall(tagsRequest).execute()
            if (tagsResponse.isSuccessful) {
                cachedTags = tagsResponse.parseAs<ApiResponse<List<GenreTagDto>>>().data
            }
        }
    }

    // ============================= Details ================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        checkLogin()
        val slug = manga.url.removePrefix("/manga/")
        return GET("$apiUrl/api/mangas/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ApiResponse<MangaDto>>()
        return result.data.toSManga(apiUrl)
    }

    // ============================= Chapters ===============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            checkLogin()
            val slug = manga.url.removePrefix("/manga/")

            val detailsRequest = GET("$apiUrl/api/mangas/$slug", headers)
            val detailsResponse = client.newCall(detailsRequest).execute()
            val mangaData = detailsResponse.parseAs<ApiResponse<MangaDto>>().data
            val mangaId = mangaData.id

            val allChapters = mutableListOf<ChapterDto>()
            var currentPage = 1
            var hasMore = true

            while (hasMore) {
                val chaptersUrl = "$apiUrl/api/chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("mangaId", mangaId)
                    .addQueryParameter("page", currentPage.toString())
                    .addQueryParameter("limit", CHAPTERS_LIMIT.toString())
                    .addQueryParameter("order", "desc")
                    .build()

                val chaptersRequest = GET(chaptersUrl, headers)
                val chaptersResponse = client.newCall(chaptersRequest).execute()
                val result = chaptersResponse.parseAs<ApiListResponse<ChapterDto>>()

                allChapters.addAll(result.data)
                hasMore = result.pagination?.hasNextPage() ?: false
                currentPage++
            }

            allChapters.map { it.toSChapter(slug, dateFormat) }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        throw UnsupportedOperationException()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        throw UnsupportedOperationException()
    }

    // ============================= Pages ==================================

    override fun pageListRequest(chapter: SChapter): Request {
        checkLogin()
        val chapterId = chapter.url.removePrefix("/chapter/").substringBefore("/")
        return GET("$apiUrl/api/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ApiResponse<ChapterPagesDto>>()
        return result.data.pages.sortedBy { it.pageNumber }.mapIndexed { index, page ->
            Page(index, imageUrl = "$apiUrl${page.imageUrl}")
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    // ============================= Utils ==================================

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.removePrefix("/manga/")
        return "$baseUrl/obra/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val parts = chapter.url.removePrefix("/chapter/").split("/")
        val mangaSlug = parts.getOrElse(1) { "" }
        val chapterNumber = parts.getOrElse(2) { "" }
        return "$baseUrl/ler/$mangaSlug/$chapterNumber"
    }

    // ============================= Filters ================================

    override fun getFilterList(): FilterList {
        launchIO { fetchFilters() }

        val showNsfw = runCatching {
            preferences.getBoolean(PREF_SHOW_NSFW_FILTERS, false)
        }.getOrDefault(false)

        val filteredGenres = (if (showNsfw) cachedGenres else cachedGenres.filter { !it.isNsfw })
            .map { it.name to it.id }
        val filteredTags = (if (showNsfw) cachedTags else cachedTags.filter { !it.isNsfw })
            .map { it.name to it.id }

        return getFilters(filteredGenres, filteredTags)
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

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_NSFW_FILTERS
            title = "Mostrar filtros +18"
            summary = "Exibe gêneros e tags NSFW nos filtros de busca"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    companion object {
        private const val PAGE_LIMIT = 24
        private const val CHAPTERS_LIMIT = 100
        private const val PREF_EMAIL = "skkytoons_email"
        private const val PREF_PASSWORD = "skkytoons_password"
        private const val PREF_SHOW_NSFW_FILTERS = "skkytoons_show_nsfw_filters"
        private const val LOGIN_REQUIRED_MESSAGE = "Por favor, insira um email e uma senha nas configurações para logar"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
