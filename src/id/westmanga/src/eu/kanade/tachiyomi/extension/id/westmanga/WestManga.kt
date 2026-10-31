package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class WestManga : KeiSource() {
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
        val slug = when (url.pathSegments.firstOrNull()) {
            "comic", "manga" -> url.pathSegments.getOrNull(1)
            else -> null
        } ?: return null
        val api = "$baseUrl/api/comic/$slug".toHttpUrl()

        return apiGet<Data<Manga>>(api).data
            .toSManga(baseUrl)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        require(path.size == 3) { "Migrate from $name to $name" }
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
        require(path.isNotEmpty()) { "Refresh Chapter List" }
        val slug = path.first()

        val url = "$baseUrl/api/v/$slug".toHttpUrl()
        val data = apiGet<Data<ImageList>>(url).data
        return data.images.mapIndexed { idx, img ->
            val imgUrl = if (img.startsWith("http")) img else "$baseUrl$img"
            Page(idx, imageUrl = imgUrl)
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

    private suspend fun getVisitToken(forceRefresh: Boolean = false): String? = visitTokenMutex.withLock {
        val now = Clock.System.now()
        if (!forceRefresh && visitToken != null && now < visitTokenExpiresAt - 5.minutes) {
            return@withLock visitToken
        }

        val url = "$baseUrl/api/visit-token".toHttpUrl()
        val response = runCatching {
            client.get(url).parseAs<VisitTokenResponse>()
        }.getOrNull()

        if (response?.status == true && !response.token.isNullOrBlank()) {
            visitToken = response.token
            visitTokenExpiresAt = response.expiresAt?.let(Instant::fromEpochSeconds) ?: (now + 1.hours)
            return@withLock visitToken
        }

        if (forceRefresh) {
            visitToken = null
            visitTokenExpiresAt = Instant.DISTANT_PAST
        }
        return@withLock visitToken
    }

    private suspend fun apiHeaders(includeToken: Boolean = true): Headers = headersBuilder().apply {
        getVisitToken()?.let { set("X-WM-Visit", it) }
        if (includeToken) {
            bearerToken()?.let { set("Authorization", "Bearer $it") }
        }
    }.build()

    private suspend inline fun <reified T> apiGet(url: HttpUrl, includeToken: Boolean = true): T = try {
        client.get(url, apiHeaders(includeToken)).parseAs<T>()
    } catch (e: Exception) {
        if (e is HttpException && (e.code == 403 || e.code == 404)) {
            getVisitToken(forceRefresh = true)
            client.get(url, apiHeaders(includeToken)).parseAs<T>()
        } else {
            throw e
        }
    }
}
