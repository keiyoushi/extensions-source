package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class WestManga : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.pathSegments.first() == "api" }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", SortFilter.popular)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.latest)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/contents".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "20")
            addQueryParameter("type", "Comic")
            filters.filterIsInstance<UrlFilter>().forEach {
                it.addToUrl(this)
            }
        }.build()

        val data = apiGet<PaginatedData<BrowseManga>>(url)
        val entries = data.data.map { it.toSManga(baseUrl) }
        return MangasPage(entries, data.paginator.hasNextPage())
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/api/contents/genres".toHttpUrl()
        return apiGet<JsonElement>(url, includeToken = false)
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Data<List<ApiGenre>>>()?.data
            ?.map { it.name to it.id.toString() }

        return FilterList(
            listOfNotNull(
                SortFilter(),
                StatusFilter(),
                CountryFilter(),
                ColorFilter(),
                genres?.takeIf { it.isNotEmpty() }?.let { GenreFilter(it) },
            ),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if ("westmanga" !in url.host.split(".")) return null
        val slug = when (url.pathSegments.firstOrNull()) {
            "comic", "manga" -> url.pathSegments.getOrNull(1)
            else -> null
        } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug/" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        require(path.size == 3) { "Migrasikan dari $name ke $name" }
        val slug = path[1]
        val api = "$baseUrl/api/comic/$slug".toHttpUrl()

        val data = apiGet<Data<Manga>>(api).data
        return SMangaUpdate(
            data.toSManga(baseUrl),
            data.chapters.map { it.toSChapter() },
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]
        return "$baseUrl/comic/$slug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        require(path.isNotEmpty()) { "Muat ulang daftar chapter" }
        val slug = path.first()

        val url = "$baseUrl/api/v/$slug".toHttpUrl()
        val data = apiGet<Data<ImageList>>(url).data
        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = baseUrl.toHttpUrl().resolve(img)!!.toString())
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.first()
        return "$baseUrl/view/$slug"
    }

    private var tokenCache: String? = null
    private var tokenChecked = false

    private suspend fun bearerToken(): String? {
        if (tokenChecked) return tokenCache
        tokenCache = runCatching {
            getLocalStorage(baseUrl, "global-app-storage")?.let {
                it.parseAs<GlobalAppStorage>().state.session.accsessToken?.takeIf { token -> token.isNotBlank() }
            }
        }.getOrNull()
        tokenChecked = true
        return tokenCache
    }

    private val visitTokenMutex = Mutex()
    private var visitToken: String? = null
    private var visitTokenExpiresAt = Instant.DISTANT_PAST

    private suspend fun getVisitToken(stale: String? = null): String = visitTokenMutex.withLock {
        val now = Clock.System.now()
        visitToken?.takeIf { it != stale && now < visitTokenExpiresAt - 5.minutes }?.let { return@withLock it }

        val url = "$baseUrl/api/visit-token".toHttpUrl()
        val response = client.get(url, Fetch.XHR).parseAs<VisitTokenResponse>()
        visitTokenExpiresAt = Instant.fromEpochSeconds(response.expiresAt)
        visitToken = response.token
        response.token
    }

    private suspend fun apiHeaders(token: String, includeToken: Boolean): Headers = headersBuilder().apply {
        set("X-WM-Visit", token)
        if (includeToken) {
            bearerToken()?.let { set("Authorization", "Bearer $it") }
        }
    }.build()

    private suspend inline fun <reified T> apiGet(url: HttpUrl, includeToken: Boolean = true): T = api(url, includeToken).parseAs<T>()

    private suspend fun api(url: HttpUrl, includeToken: Boolean): Response {
        val token = getVisitToken()
        val response = client.get(url, apiHeaders(token, includeToken), Fetch.XHR, ensureSuccess = false)
        if (response.isSuccessful) return response
        response.close()
        // A token answers 40 API requests, then 429s until it expires; a stale one gets 403 + X-WM-Visit-Error.
        val tokenRejected = response.code == 429 || (response.code == 403 && response.header("X-WM-Visit-Error") != null)
        if (!tokenRejected) throw HttpException(response.code)
        return client.get(url, apiHeaders(getVisitToken(stale = token), includeToken), Fetch.XHR)
    }
}
