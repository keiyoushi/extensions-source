package eu.kanade.tachiyomi.extension.all.bookorbit

import android.text.InputType
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException

@Source
abstract class BookOrbit :
    KeiSource(),
    ConfigurableSource,
    UnmeteredSource {

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    private val username get() = preferences.getString(PREF_USERNAME, "")!!

    private val password get() = preferences.getString(PREF_PASSWORD, "")!!

    // Access tokens live 15 minutes by default; the rotating refresh token is persisted so a
    // restart doesn't open a new server session.
    @Volatile
    private var accessToken: String? = null

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::authIntercept)
        .dns(Dns.SYSTEM) // DNS over HTTPS can't resolve LAN hosts

    private fun authIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrl.toHttpUrl().host || request.url.encodedPath.startsWith(AUTH_PATH)) {
            return chain.proceed(request)
        }

        val token = accessToken ?: authenticate(chain, null)
        val response = chain.proceed(request.withToken(token))
        if (response.code != 401) return response

        response.close()
        return chain.proceed(request.withToken(authenticate(chain, token)))
    }

    private fun Request.withToken(token: String) = newBuilder().header("Authorization", "Bearer $token").build()

    @Synchronized
    private fun authenticate(chain: Interceptor.Chain, staleToken: String?): String {
        accessToken?.takeIf { it != staleToken }?.let { return it }

        val storedRefreshToken = preferences.getString(PREF_REFRESH_TOKEN, "")!!
        val credentials = storedRefreshToken.takeIf { it.isNotBlank() }
            ?.let { postAuth(chain, "refresh", RefreshRequestDto(it).toJsonRequestBody()) }
            ?: logIn(chain)

        preferences.edit().putString(PREF_REFRESH_TOKEN, credentials.refreshToken).apply()
        accessToken = credentials.accessToken
        return credentials.accessToken
    }

    private fun logIn(chain: Interceptor.Chain): CredentialsDto {
        if (username.isBlank() || password.isBlank()) {
            throw IOException("Set your username and password in the extension settings")
        }
        val body = LoginRequestDto(username, password, "native", "Mihon").toJsonRequestBody()
        return postAuth(chain, "login", body) ?: throw IOException("BookOrbit login failed, check your username and password")
    }

    private fun postAuth(chain: Interceptor.Chain, endpoint: String, body: RequestBody): CredentialsDto? {
        val request = Request.Builder()
            .url("$baseUrl$AUTH_PATH$endpoint")
            .headers(headers)
            .post(body)
            .build()
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            response.close()
            return null
        }
        return response.parseAs<CredentialsDto>()
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSeriesPage(page, "", "name", "asc", null)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSeriesPage(page, "", "lastAddedAt", "desc", null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()
        val libraryId = filters.firstInstanceOrNull<LibraryFilter>()?.selectedId
        return getSeriesPage(page, query, sort.sort, sort.order, libraryId)
    }

    private suspend fun getSeriesPage(page: Int, query: String, sort: String, order: String, libraryId: Int?): MangasPage {
        val url = "$baseUrl/api/v1/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("size", PAGE_SIZE.toString())
            .addQueryParameter("sort", sort)
            .addQueryParameter("order", order)
            .apply {
                if (query.isNotBlank()) addQueryParameter("q", query)
                libraryId?.let { addQueryParameter("libraryId", it.toString()) }
            }
            .build()

        val data = client.get(url).parseAs<SeriesPageDto>()
        return MangasPage(data.items.map { it.toSManga(baseUrl) }, page * PAGE_SIZE < data.total)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val seriesId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val info = getSeriesBooksPage(seriesId.toString(), 0, 1).seriesInfo

        return SManga.create().apply {
            this.url = seriesId.toString()
            title = info.name
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    // Details and chapters both come from the series' book list, so always return both.
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val firstPage = getSeriesBooksPage(manga.url, 0, BOOKS_PAGE_SIZE)
        val books = firstPage.items.toMutableList()
        var page = 1
        while (books.size < firstPage.total) {
            books += getSeriesBooksPage(manga.url, page++, BOOKS_PAGE_SIZE).items
        }

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = firstPage.seriesInfo.name
            author = firstPage.seriesInfo.authors.joinToString()
            genre = books.flatMap { it.genres }.distinct().joinToString()
            thumbnail_url = books.lastOrNull()?.let { thumbnailUrl(baseUrl, it.id) } ?: manga.thumbnail_url
        }

        return SMangaUpdate(updatedManga, books.filter { it.isComic }.map { it.toSChapter() })
    }

    private suspend fun getSeriesBooksPage(seriesId: String, page: Int, size: Int): SeriesBooksPageDto {
        val url = "$baseUrl/api/v1/series/$seriesId/books".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("size", size.toString())
            .addQueryParameter("sort", "seriesIndex")
            .addQueryParameter("order", "desc")
            .build()
        return client.get(url).parseAs()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/book/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val book = client.get("$baseUrl/api/v1/books/${chapter.url}").parseAs<BookDetailDto>()
        val fileId = book.files.firstOrNull { it.isComic }?.id ?: return emptyList()
        val pageCount = client.get("$baseUrl/api/v1/cbz/files/$fileId/pages").parseAs<PageCountDto>().pageCount

        return List(pageCount) { index ->
            Page(index, imageUrl = "$baseUrl/api/v1/cbz/files/$fileId/pages/$index")
        }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/v1/libraries").parseAs<List<LibraryDto>>().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val libraries = data?.parseAs<List<LibraryDto>>().orEmpty()
        return FilterList(
            listOfNotNull(
                libraries.takeIf { it.isNotEmpty() }?.let { LibraryFilter(it) },
                SortFilter(),
            ),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addCredentialPreference(PREF_USERNAME, "Username", InputType.TYPE_CLASS_TEXT, masked = false)
        screen.addCredentialPreference(
            PREF_PASSWORD,
            "Password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            masked = true,
        )
    }

    private fun PreferenceScreen.addCredentialPreference(key: String, title: String, inputType: Int, masked: Boolean) {
        fun summaryOf(value: String) = when {
            value.isBlank() -> "Not set"
            masked -> "•".repeat(value.length)
            else -> value
        }

        EditTextPreference(context).apply {
            this.key = key
            this.title = title
            dialogTitle = title
            summary = summaryOf(preferences.getString(key, "")!!)
            setOnBindEditTextListener { it.inputType = inputType }
            setOnPreferenceChangeListener { _, newValue ->
                summary = summaryOf(newValue as String)
                // New credentials must not be shadowed by the old account's session.
                preferences.edit().remove(PREF_REFRESH_TOKEN).apply()
                accessToken = null
                true
            }
        }.also(::addPreference)
    }
}

private const val AUTH_PATH = "/api/v1/auth/"
private const val PAGE_SIZE = 50
private const val BOOKS_PAGE_SIZE = 100

private const val PREF_USERNAME = "username"
private const val PREF_PASSWORD = "password"
private const val PREF_REFRESH_TOKEN = "refresh_token"
