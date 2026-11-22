package eu.kanade.tachiyomi.extension.en.fanon

import android.util.LruCache
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Instant

@Source
abstract class Fanon : KeiSource() {

    override val supportsFilterFetching = true

    // Next page's cursor and the listing it belongs to
    private var nextCursor: Pair<String, JsonElement>? = null

    // Episodes granted to this session, by "slug:number"
    private val grants = LruCache<String, Instant>(32)

    private val grantMutex = Mutex()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::imageInterceptor)
        .rateLimit(3) { it.host != MEDIA_HOST }

    override suspend fun getPopularManga(page: Int) = getFanfics(page, "Popular", null)

    override suspend fun getLatestUpdates(page: Int) = getFanfics(page, "Latest", null)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            val sortBy = filters.firstInstanceOrNull<SortFilter>()?.value ?: "Popular"
            return getFanfics(page, sortBy, filters.firstInstanceOrNull<FandomFilter>()?.value)
        }
        val results = convex<SearchArgs, SearchResultsDto>("action", "search:globalElasticsearch", SearchArgs(query.trim()))
        return MangasPage(results.fanfics.filter { it.medium == COMIC }.map { it.toSManga() }, false)
    }

    // A series is "/fanfics/<slug>-<name>" on www or the bare domain; the site opens it by the slug alone too
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.topPrivateDomain() != baseUrl.toHttpUrl().topPrivateDomain() || url.pathSegments.firstOrNull() != "fanfics") return null
        val slug = url.pathSegments.getOrNull(1)?.substringBefore('-')?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/fanfics/$slug" }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = getFanficPage(getMangaUrl(manga))
        val slug = data.fanfic.fanficSlug
        val episodes = data.comicEpisodes
            .sortedByDescending { it.number }
            .map { it.toSChapter(slug) }
        return SMangaUpdate(data.toSManga(), episodes)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // "/fanfics/<slug>/episodes/<name>"
        val (_, slug, _, name) = getChapterUrl(chapter).toHttpUrl().pathSegments
        val data = getFanficPage("$baseUrl/fanfics/$slug")
        val episode = data.episodes.first { it.name == name }
        val fancomic = episode.fancomic ?: return emptyList()
        grantEpisode(slug, episode.number)
        // As the site's reader: pageCount pages, a version only where pages[] has one (older episodes have a placeholder)
        return List(fancomic.pageCount) { index ->
            val version = fancomic.pages.getOrNull(index)?.updatedAt?.toLong() ?: 1
            Page(index, imageUrl = "$MEDIA_URL/v_$version/w_1440/f_auto/c_fill/src/Fanfics/$slug/Episode-${episode.number}/${index + 1}")
        }
    }

    override suspend fun fetchFilterData(): JsonElement = convex<EmptyArgs, List<FandomDto>>("query", "fandoms:getAllFandomsWithoutFilter", EmptyArgs)
        .sortedBy { it.name.lowercase() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            Filter.Header("Text search ignores the filters"),
            SortFilter(),
            data?.parseAs<List<FandomDto>>()?.let(::FandomFilter),
        ),
    )

    private suspend fun getFanfics(page: Int, sortBy: String, fandomId: String?): MangasPage {
        val key = "$sortBy:$fandomId"
        var cursor = if (page == 1) {
            null
        } else {
            nextCursor?.takeIf { it.first == key }?.second ?: return MangasPage(emptyList(), false)
        }
        // Fandom lists mix in videos: skip ahead past pages without comics
        while (true) {
            val result = if (fandomId == null) {
                convex<ListArgs, FanficListDto>("query", "fanfics:getAllVisibleFanfics", ListArgs(PAGE_SIZE, sortBy, fanficFilter = "Comic", startIndexKey = cursor))
            } else {
                convex<ListArgs, FanficListDto>("query", "fanfics:getHomeFanfics", ListArgs(PAGE_SIZE, sortBy, fandomIds = listOf(fandomId), startIndexKey = cursor))
            }
            val mangas = result.fanfics.filter { it.medium == COMIC }.map { it.toSManga() }
            cursor = result.nextStartIndexKey
            nextCursor = cursor?.let { key to it }
            if (mangas.isNotEmpty() || !result.hasMore || cursor == null) return MangasPage(mangas, result.hasMore)
        }
    }

    private suspend fun getFanficPage(url: String): FanficPageDto = client.get(url, Fetch.DOCUMENT)
        .extractNextJs<FanficPageDto> { it is JsonObject && "fanfic" in it && "episodes" in it }
        ?: throw Exception("Series data not found on the page")

    private suspend inline fun <reified A, reified T> convex(kind: String, path: String, args: A): T {
        val response = client.post("$API_URL/$kind", Fetch.XHR, ConvexRequest(path, args, "json").toJsonRequestBody())
            .parseAs<ConvexResponse<T>>()
        return response.value?.takeIf { response.status == "success" } ?: throw IOException("Fanon API error ($path)")
    }

    // Page images need a per-episode grant cookie, valid one hour; the site's reader renews it when an image fails
    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sentAt = Clock.System.now()
        val response = chain.proceed(request)
        if (response.code != 403 || request.url.host != MEDIA_HOST) return response

        val segments = request.url.pathSegments
        val fanfics = segments.indexOf("Fanfics").takeIf { it >= 0 } ?: return response
        val slug = segments.getOrNull(fanfics + 1) ?: return response
        val number = segments.getOrNull(fanfics + 2)?.removePrefix("Episode-")?.toIntOrNull() ?: return response
        response.close()
        try {
            runBlocking { grantEpisode(slug, number, sentAt) }
        } catch (e: Exception) {
            throw e as? IOException ?: IOException(e.message, e)
        }
        return chain.proceed(request)
    }

    // One grant per episode; an image sent before the latest grant just retries
    private suspend fun grantEpisode(slug: String, number: Int, sentAt: Instant? = null) = grantMutex.withLock {
        val key = "$slug:$number"
        if (sentAt != null && grants[key]?.let { it > sentAt } == true) return@withLock
        val body = EpisodeAuthRequest(slug, listOf(number)).toJsonRequestBody()
        val fetch = Fetch.XHR.from("/fanfics/$slug/episodes/Episode-$number")
        // 403 {"allowed":false}: a guest, or an episode the account hasn't unlocked
        client.post("$baseUrl/api/v2/episode/auth/refresh", fetch, body, ensureSuccess = false).use {
            if (it.code == 403) throw Exception(if (isLoggedIn()) "Unlock this episode on Fanon to read it" else "Log in via WebView to read this episode")
            if (!it.isSuccessful) throw HttpException(it.code)
        }
        grants.put(key, Clock.System.now())
    }

    private fun isLoggedIn() = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).any { it.name == "X-Fanon-User-Id" }

    companion object {
        private const val API_URL = "https://api.convex.fanon.co/api"
        private const val PAGE_SIZE = 24
        private const val COMIC = "FANCOMIC"
        private val MEDIA_HOST = MEDIA_URL.toHttpUrl().host
    }
}
