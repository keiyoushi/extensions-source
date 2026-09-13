package eu.kanade.tachiyomi.extension.pt.roxinha

import android.text.InputType
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.io.IOException

@Source
abstract class Roxinha :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    private var token: String? = null

    override val client by lazy {
        network.client.newBuilder()
            .addInterceptor(::authIntercept)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 24
        return GET("$apiUrl/manga/search/advanced?sort=views&order=DESC&limit=24&offset=$offset", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()
        val (mangas, hasMore) = dto.toMangasPage(baseUrl)
        return MangasPage(mangas, hasMore)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * 24
        return GET("$apiUrl/manga/search/advanced?sort=updatedAt&order=DESC&limit=24&offset=$offset", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val offset = (page - 1) * 24
        val url = "$apiUrl/manga/search/advanced".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "24")
            addQueryParameter("offset", offset.toString())
            addQueryParameter("mode", "default")

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            if (sortFilter != null) {
                val sortFields = arrayOf("title", "updatedAt", "views", "avgRating")
                val sortIndex = sortFilter.state?.index ?: 2
                addQueryParameter("sort", sortFields[sortIndex])
                addQueryParameter("order", if (sortFilter.state?.ascending == true) "ASC" else "DESC")
            } else {
                addQueryParameter("sort", "title")
                addQueryParameter("order", "ASC")
            }

            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
            if (statusFilter != null && statusFilter.state != 0) {
                val statusFields = arrayOf("", "ongoing", "completed")
                addQueryParameter("status", statusFields[statusFilter.state])
            }

            val typeFilter = filters.firstInstanceOrNull<TypeFilter>()
            if (typeFilter != null && typeFilter.state != 0) {
                val typeFields = arrayOf("", "Manga", "Manhua", "Manhwa", "Webtoon")
                addQueryParameter("type", typeFields[typeFilter.state])
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDto>()
        return dto.toSManga(baseUrl)
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<MangaDto>()
        return dto.toSChapters()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/chapter/${chapter.url}"

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val chapterUrl = "$apiUrl/manga/chapter/${chapter.url}"

        val accessRes = client.newCall(GET("$chapterUrl/access", headers)).execute()
        if (!accessRes.isSuccessful) {
            val message = accessRes.errorMessage()
            throw Exception(if (accessRes.code == 401) "$message. $LOGIN_HINT" else message)
        }
        val accessDto = accessRes.parseAs<TicketDto>()
        val accessHeaders = headersBuilder().set("x-chapter-access", accessDto.ticket).build()

        val chapterRes = client.newCall(GET(chapterUrl, accessHeaders)).execute()
        val chapterDto = chapterRes.parseAs<ChapterDetailsDto>()
        return Observable.just(chapterDto.toPages(baseUrl))
    }

    override fun pageListRequest(chapter: SChapter) = throw UnsupportedOperationException()

    override fun pageListParse(response: Response) = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = EMAIL_PREF
            title = "E-mail"
            summary = LOGIN_HINT
            setDefaultValue("")
            setOnPreferenceChangeListener { _, _ ->
                token = null
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PASSWORD_PREF
            title = "Senha"
            summary = LOGIN_HINT
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            setOnPreferenceChangeListener { _, _ ->
                token = null
                true
            }
        }.also(screen::addPreference)
    }

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith("/api/")) {
            return chain.proceed(request)
        }

        val token = getToken() ?: return chain.proceed(request)
        val response = chain.proceed(request.withToken(token))

        if (response.code != 401) {
            return response
        }

        response.close()
        this.token = null
        val newToken = getToken() ?: return chain.proceed(request)
        return chain.proceed(request.withToken(newToken))
    }

    private fun Request.withToken(token: String) = newBuilder()
        .header("Authorization", "Bearer $token")
        .build()

    @Synchronized
    private fun getToken(): String? {
        token?.let { return it }

        val email = preferences.getString(EMAIL_PREF, "").orEmpty()
        val password = preferences.getString(PASSWORD_PREF, "").orEmpty()
        if (email.isEmpty() || password.isEmpty()) {
            return null
        }

        val body = LoginRequestDto(email.trim(), password).toJsonRequestBody()
        val response = network.client.newCall(POST("$apiUrl/auth/login", headers, body)).execute()

        if (!response.isSuccessful) {
            throw IOException(response.errorMessage())
        }

        return response.parseAs<LoginResponseDto>().token.also { token = it }
    }

    private fun Response.errorMessage(): String = use {
        runCatching { it.parseAs<ErrorDto>().error }.getOrDefault("Erro HTTP ${it.code}")
    }

    companion object {
        private const val EMAIL_PREF = "email"
        private const val PASSWORD_PREF = "password"
        private const val LOGIN_HINT = "Informe o e-mail e a senha da sua conta da Roxinha para ler os capítulos"
    }
}
