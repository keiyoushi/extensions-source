package eu.kanade.tachiyomi.extension.pt.sssscanlator

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.io.IOException

class YomuComics :
    HttpSource(),
    ConfigurableSource {

    override val name = "Yomu Comics"

    override val baseUrl = "https://yomu.com.br"

    override val lang = "pt-BR"

    override val supportsLatest = true

    // SSSScanlator
    override val id = 1497838059713668619

    private val json: Json by injectLazy()

    private val preferences = getPreferences()

    private var sessionToken: String? = null

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val token = synchronized(this) {
            sessionToken ?: login()
        }

        val newRequest = if (token.isNotEmpty()) {
            request.newBuilder()
                .header("Cookie", "__Secure-authjs.session-token=$token")
                .build()
        } else {
            request
        }

        chain.proceed(newRequest)
    }

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .rateLimit(2)
            .addInterceptor(authInterceptor)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun login(): String {
        val email = preferences.getString(PREF_EMAIL, "") ?: ""
        val password = preferences.getString(PREF_PASSWORD, "") ?: ""

        if (email.isEmpty() || password.isEmpty()) return ""

        val client = network.cloudflareClient

        return try {
            val csrfRequest = GET("$baseUrl/api/auth/csrf", headers)
            val csrfResponse = client.newCall(csrfRequest).execute()
            if (!csrfResponse.isSuccessful) {
                csrfResponse.close()
                return ""
            }
            val csrfToken = csrfResponse.parseAs<CsrfDto>().csrfToken

            val loginBody = FormBody.Builder()
                .add("email", email)
                .add("password", password)
                .add("csrfToken", csrfToken)
                .add("callbackUrl", "/")
                .build()

            val loginRequest = POST("$baseUrl/api/auth/callback/credentials?", headers, loginBody)
            val loginResponse = client.newCall(loginRequest).execute()
            val cookies = loginResponse.headers.values("Set-Cookie")
            loginResponse.close()

            val token = cookies.find { it.startsWith("__Secure-authjs.session-token=") }
                ?.substringAfter("=")
                ?.substringBefore(";")
                ?: return ""

            sessionToken = token
            token
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/home", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val homeDto = response.parseAs<HomeDto>()
        val mangas = homeDto.featuredManga.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/updates?page=$page&limit=50", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val updatesDto = response.parseAs<UpdatesDto>()
        val mangas = updatesDto.updates.map { it.toSManga() }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/public/series".toHttpUrl().newBuilder()

        val pageStr = page.toString()
        val limitStr = "12"
        var sortStr = "createdAt"
        var orderStr = "desc"

        filters.forEach { filter ->
            if (filter is SortFilter) {
                sortStr = filter.toUriPart()
                if (sortStr == "name") {
                    orderStr = "asc"
                }
            }
        }

        url.addQueryParameter("page", pageStr)
        url.addQueryParameter("limit", limitStr)
        url.addQueryParameter("sort", sortStr)
        url.addQueryParameter("order", orderStr)

        if (query.isNotEmpty()) {
            url.addQueryParameter("query", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("genre", filter.toUriPart())
                    }
                }

                is TypeFilter -> {
                    if (filter.state != 0) {
                        val part = filter.toUriPart()
                        url.addQueryParameter("type", part)
                        if (part == "MANGA") {
                            url.addQueryParameter("subType", "MANGA")
                        }
                    }
                }

                is StatusFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("status", filter.toUriPart())
                    }
                }

                is AdultFilter -> {
                    if (filter.state) {
                        url.addQueryParameter("showAdult", "true")
                    }
                }

                else -> {}
            }
        }

        val searchHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/obras")
            .build()

        return GET(url.build(), searchHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponseDto>()
        val mangas = result.results.map { it.toSManga() }
        val hasNextPage = result.pagination?.let { it.page < it.pages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val path = manga.url
            .removePrefix(baseUrl)
            .removePrefix("/")

        val slug = if (path.startsWith("obra/")) {
            path.removePrefix("obra/")
        } else {
            path
        }

        return GET("$baseUrl/api/public/series/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val seriesDto = response.parseAs<SeriesDto>()
        val slug = response.request.url.pathSegments.last()
        return seriesDto.toSManga(slug)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // =============================== Chapters =============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesDto = response.parseAs<SeriesDto>()
        val slug = response.request.url.pathSegments.last()
        return seriesDto.chapters.map { it.toSChapter(slug, seriesDto.id) }.reversed()
    }

    // ================================ Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val url = if (chapter.url.startsWith("http")) {
            chapter.url.toHttpUrl()
        } else {
            "$baseUrl${chapter.url}".toHttpUrl()
        }

        val mangaId = url.queryParameter("id")
            ?: throw IOException("ID da obra não encontrado na URL do capítulo")
        val chapterNumber = url.pathSegments.last().removePrefix("capitulo-").replace("-", ".")

        return GET("$baseUrl/api/public/chapters/$mangaId/$chapterNumber", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val pagesDto = response.parseAs<ChapterPagesDto>()

        return pagesDto.pages.mapIndexed { index, pageDto ->
            Page(index, imageUrl = baseUrl + pageDto.url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Settings ==============================
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val emailPref = EditTextPreference(screen.context).apply {
            key = PREF_EMAIL
            title = "Email"
            summary = "Email para login na Yomu Comics"
            setDefaultValue("")
            dialogTitle = "Email"
        }
        val passwordPref = EditTextPreference(screen.context).apply {
            key = PREF_PASSWORD
            title = "Senha"
            summary = "Senha para login na Yomu Comics"
            setDefaultValue("")
            dialogTitle = "Senha"
        }

        screen.addPreference(emailPref)
        screen.addPreference(passwordPref)
    }

    // =============================== Filters ==============================
    override fun getFilterList() = FilterList(
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
        AdultFilter(),
        SortFilter(),
    )

    companion object {
        private const val PREF_EMAIL = "email"
        private const val PREF_PASSWORD = "password"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
