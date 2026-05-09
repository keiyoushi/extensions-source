package eu.kanade.tachiyomi.extension.all.utifa

import android.content.SharedPreferences
import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException

class Utifa :
    HttpSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val name = "Utifa"

    override val lang = "all"

    override val baseUrl = "https://bx.yaimer.com"

    private val apiUrl = "$baseUrl/api"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::tokenInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("User-Agent", USER_AGENT)
        .set("Referer", baseUrl)
        .set("Accept", "application/json, text/plain, */*")

    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != BASE_HOST || request.url.encodedPath.endsWith("/user/login")) {
            return chain.proceed(request)
        }

        val token = getOrFetchToken()
        val authedRequest = request.withToken(token)
        val response = chain.proceed(authedRequest)
        if (!response.isInvalidTokenApiResponse()) {
            return response
        }

        response.close()
        val refreshedToken = refreshToken(force = true)
        return chain.proceed(request.withToken(refreshedToken))
    }

    override fun popularMangaRequest(page: Int): Request = mangaListRequest(
        page = page,
        query = "",
        filters = FilterList(),
        fallbackOrderBy = "views",
        fallbackOrder = "desc",
    )

    override fun popularMangaParse(response: Response): MangasPage = response.parseMangaPage()

    override fun latestUpdatesRequest(page: Int): Request = mangaListRequest(
        page = page,
        query = "",
        filters = FilterList(),
        fallbackOrderBy = "updateTime",
        fallbackOrder = "desc",
    )

    override fun latestUpdatesParse(response: Response): MangasPage = response.parseMangaPage()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val mangaId = query.toUtifaMangaId()
        if (mangaId != null) {
            return fetchMangaDetails(
                SManga.create().apply { url = "/manga/detail/$mangaId" },
            ).map { MangasPage(listOf(it), false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = mangaListRequest(
        page = page,
        query = query,
        filters = filters,
        fallbackOrderBy = "updateTime",
        fallbackOrder = "desc",
    )

    override fun searchMangaParse(response: Response): MangasPage = response.parseMangaPage()

    private fun mangaListRequest(
        page: Int,
        query: String,
        filters: FilterList,
        fallbackOrderBy: String,
        fallbackOrder: String,
    ): Request {
        var orderBy = fallbackOrderBy
        var order = fallbackOrder
        var theme: String? = null
        var grade: String? = null
        var updateStatus: String? = null

        filters.forEach { filter ->
            when (filter) {
                is ThemeFilter -> theme = filter.selectedValue().ifBlank { null }
                is GradeFilter -> grade = filter.selectedValue().ifBlank { null }
                is UpdateStatusFilter -> updateStatus = filter.selectedValue().ifBlank { null }
                is UtifaSortFilter -> {
                    val state = filter.state ?: return@forEach
                    orderBy = when (state.index) {
                        1 -> "views"
                        2 -> "likes"
                        3 -> "createTime"
                        4 -> "title"
                        else -> "updateTime"
                    }
                    order = if (state.ascending) "asc" else "desc"
                }
                else -> {}
            }
        }

        val body = MangaListRequest(
            pageNum = page,
            pageSize = PAGE_SIZE,
            keyword = query.takeIf(String::isNotBlank),
            orderBy = orderBy,
            order = order,
            theme = theme,
            grade = grade,
            updateStatus = updateStatus,
        ).toJsonRequestBody()

        return POST("$apiUrl/manga/common/list", headers, body)
    }

    private fun Response.parseMangaPage(): MangasPage {
        val page = parseApiData<MangaPageDto>()
        return MangasPage(
            mangas = page.records.mapNotNull { it.toSManga(baseUrl) },
            hasNextPage = page.current < page.pages,
        )
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringBefore('?').substringAfterLast("/")
        val url = "$apiUrl/manga/common/get".toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseApiData<MangaDetailDto>().toSManga(baseUrl)

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val detail = response.parseApiData<MangaDetailDto>()
        return detail.chapters
            .mapIndexedNotNull { index, chapter -> chapter.toSChapter(index) }
            .asReversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringBefore('?').substringAfterLast("/")
        val url = "$apiUrl/manga/common/getChapterList".toHttpUrl().newBuilder()
            .addQueryParameter("chapterId", chapterId)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val detail = response.parseApiData<ChapterDetailDto>()
        return detail.chapters.mapIndexedNotNull { index, file -> file.toPage(index, baseUrl) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Accept", "image/*,*/*;q=0.8")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun getFilterList(): FilterList = FilterList(
        UtifaSortFilter(),
        ThemeFilter(),
        GradeFilter(),
        UpdateStatusFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_USERNAME
            title = "Username"
            summary = preferences.getString(PREF_USERNAME, "").orEmpty().ifBlank { "Optional" }
            setDefaultValue("")
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Password"
            summary = preferences.getString(PREF_PASSWORD, "").orEmpty().maskSecret("Optional")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setDefaultValue("")
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = PREF_VALIDITY
            title = "Login validity"
            entries = arrayOf("1 hour", "1 day", "7 days", "30 days")
            entryValues = arrayOf("3600", "86400", "604800", "2592000")
            summary = "%s"
            setDefaultValue("604800")
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREF_TOKEN
            title = "UTifaToken"
            summary = preferences.getString(PREF_TOKEN, "").orEmpty().maskSecret("Optional; auto-filled after username/password login")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setDefaultValue("")
        }.also(screen::addPreference)
    }

    private inline fun <reified T> Response.parseApiData(): T {
        val envelope = parseAs<ApiEnvelope<T>>()
        if (envelope.code != SUCCESS_CODE) {
            throw IOException(envelope.message ?: "Utifa API error: ${envelope.code}")
        }
        return envelope.data ?: throw IOException("Utifa API returned no data")
    }

    private fun getOrFetchToken(): String {
        val token = preferences.getString(PREF_TOKEN, "").orEmpty()
        if (token.isNotBlank()) {
            return token
        }
        return refreshToken()
    }

    private fun refreshToken(force: Boolean = false): String = synchronized(tokenLock) {
        val storedToken = preferences.getString(PREF_TOKEN, "").orEmpty()
        if (!force && storedToken.isNotBlank()) {
            return@synchronized storedToken
        }

        val username = preferences.getString(PREF_USERNAME, "").orEmpty()
        val password = preferences.getString(PREF_PASSWORD, "").orEmpty()
        if (username.isBlank() || password.isBlank()) {
            return@synchronized ""
        }

        val validity = preferences.getString(PREF_VALIDITY, "604800").orEmpty().toIntOrNull() ?: 604800
        val url = "$apiUrl/user/login".toHttpUrl().newBuilder()
            .addQueryParameter("userName", username)
            .addQueryParameter("passWord", password)
            .addQueryParameter("validity", validity.toString())
            .build()

        val token = network.cloudflareClient.newCall(GET(url, headersBuilder().build()))
            .execute()
            .use { it.parseApiData<LoginDataDto>().token.orEmpty() }

        preferences.edit().putString(PREF_TOKEN, token).apply()
        token
    }

    private fun Request.withToken(token: String): Request {
        if (token.isBlank()) {
            return this
        }
        return newBuilder().header(TOKEN_HEADER, token).build()
    }

    private fun Response.isInvalidTokenApiResponse(): Boolean {
        val contentType = header("Content-Type").orEmpty()
        if (!contentType.contains("application/json", ignoreCase = true)) {
            return false
        }
        return runCatching { peekBody(2048L).string().contains("\"code\":1004") }.getOrDefault(false)
    }

    private fun String.toUtifaMangaId(): Long? {
        val url = toHttpUrlOrNull() ?: return null
        if (url.host != BASE_HOST) {
            return null
        }
        val segments = url.pathSegments
        return if (segments.size >= 3 && segments[0] == "manga" && segments[1] == "detail") {
            segments[2].toLongOrNull()
        } else {
            null
        }
    }

    private fun String.maskSecret(emptyValue: String): String = if (isBlank()) emptyValue else "*".repeat(length)

    companion object {
        private const val BASE_HOST = "bx.yaimer.com"
        private const val PAGE_SIZE = 24
        private const val SUCCESS_CODE = 1000
        private const val TOKEN_HEADER = "UTifaToken"
        private const val USER_AGENT = "Mozilla/5.0 (Android) Mihon/Utifa"
        private const val PREF_USERNAME = "username"
        private const val PREF_PASSWORD = "password"
        private const val PREF_VALIDITY = "validity"
        private const val PREF_TOKEN = "token"
        private val tokenLock = Any()
    }
}
