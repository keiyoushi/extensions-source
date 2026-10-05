package eu.kanade.tachiyomi.extension.pt.roxinha
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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Source
abstract class Roxinha :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl = "$baseUrl/api"

    private val preferences by getPreferencesLazy()

    private var token: String? = null

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::authIntercept)

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.LATEST)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.pathSegments.getOrNull(1) ?: return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = id },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val offset = (page - 1) * 24
        val url = "$apiUrl/manga/search/advanced".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "24")
            addQueryParameter("offset", offset.toString())
            addQueryParameter("mode", "default")

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.selected)
                        addQueryParameter("order", filter.order)
                    }
                    is StatusFilter -> addQueryParameter("status", filter.selected)
                    is TypeFilter -> addQueryParameter("type", filter.selected)
                    else -> {}
                }
            }
        }.build()

        val dto = client.get(url).parseAs<SearchResponseDto>()
        val (mangas, hasMore) = dto.toMangasPage(baseUrl)
        return MangasPage(mangas, hasMore)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$apiUrl/manga/${manga.url}")
        val dto = response.parseAs<MangaDto>()

        return SMangaUpdate(dto.toSManga(baseUrl), dto.toSChapters())
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$apiUrl/manga/chapter/${chapter.url}"

        val accessRes = client.get("$chapterUrl/access", ensureSuccess = false)
        if (!accessRes.isSuccessful) {
            val message = accessRes.errorMessage()
            throw Exception(if (accessRes.code == 401) "$message. $LOGIN_HINT" else message)
        }
        val accessDto = accessRes.parseAs<TicketDto>()
        val accessHeaders = headersBuilder().set("x-chapter-access", accessDto.ticket).build()

        val chapterRes = client.get(chapterUrl, accessHeaders)
        val chapterDto = chapterRes.parseAs<ChapterDetailsDto>()
        return chapterDto.toPages(baseUrl)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
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
