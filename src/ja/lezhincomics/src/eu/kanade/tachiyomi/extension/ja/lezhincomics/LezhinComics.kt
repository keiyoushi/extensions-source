package eu.kanade.tachiyomi.extension.ja.lezhincomics

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.XorInterceptor
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.decodeHex
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Source
abstract class LezhinComics :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val baseHost get() = baseUrl.toHttpUrl().host

    private val apiUrl get() = baseUrl.toHttpUrl().newBuilder().addPathSegment("api").build()

    private val imageHost get() = "private-image.$baseHost"

    // The site's answer to its 18+ prompt for a logged-out reader
    override fun OkHttpClient.Builder.configureClient() = addCookie("ADULT_ENABLE" to "true")
        .rateLimit(2) { it.host == baseHost }
        .addInterceptor(::expiredImageInterceptor)
        .addInterceptor(XorInterceptor(IMAGE_KEY) { it.url.host == imageHost })

    override suspend fun getPopularManga(page: Int): MangasPage = ranking(page, "daily", ALL_GENRE)

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val today = ZonedDateTime.now(ZoneId.of("Asia/Tokyo")).dayOfWeek
        return serialization(today.name.take(3).lowercase())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) return search(page, query.trim(), filters.firstInstance<SearchTypeFilter>().value)
        val list = filters.firstInstance<ListFilter>().selected
        return list.weekDay?.let { serialization(it) }
            ?: ranking(page, list.ranking!!, filters.firstInstanceOrNull<GenreFilter>()?.hashId ?: ALL_GENRE)
    }

    private var rankingCursor: String? = null

    private suspend fun ranking(page: Int, type: String, genre: String): MangasPage {
        if (page == 1) rankingCursor = null
        val url = apiUrl.newBuilder().addPathSegment("ranking").apply {
            rankingCursor?.let { addQueryParameter("cursor", it) }
            addQueryParameter("genre_hash_id", genre)
            addQueryParameter("type", type)
            addQueryParameter("page_size", PAGE_SIZE.toString())
        }.build()
        val result = client.get(url, Fetch.XHR.from("/ranking")).parseAs<ResultDto<PageDto<RankingDto>>>().results
        rankingCursor = result.pagination.next
        return MangasPage(result.data.map { it.comic.toSManga() }, result.pagination.hasNextPage)
    }

    private suspend fun serialization(weekDay: String): MangasPage {
        // The site's own page size, which holds a whole weekday
        val url = apiUrl.newBuilder().addPathSegments("serialization/list")
            .addQueryParameter("page_size", "90")
            .addQueryParameter("week_day", weekDay)
            .build()
        val result = client.get(url, Fetch.XHR.from("/serialization")).parseAs<ResultDto<PageDto<ComicItemDto>>>().results
        return MangasPage(result.data.map { it.toSManga() }, false)
    }

    private suspend fun search(page: Int, query: String, type: String): MangasPage {
        val url = apiUrl.newBuilder().addPathSegment("search")
            .addQueryParameter("keyword", query)
            .addQueryParameter("search_type", type)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", PAGE_SIZE.toString())
            .build()
        val result = client.get(url, Fetch.XHR.from("/search")).parseAs<ResultDto<PageDto<ComicItemDto>>>().results
        return MangasPage(result.data.map { it.toSManga() }, result.pagination.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseHost || segments.getOrNull(0) != "comic") return null
        val titleId = segments.getOrNull(1)?.ifEmpty { null } ?: return null
        val manga = SManga.create().apply { this.url = titleId }
        return comic(manga)?.toSManga(manga)?.apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = { if (fetchDetails) details(manga) else manga },
        chapters = { if (fetchChapters) chapters(manga) else chapters },
    )

    private suspend fun details(manga: SManga): SManga = comic(manga)?.toSManga(manga)
        ?: throw Exception("作品が見つかりません。別のソースへ移行してください")

    // A removed title answers with empty details
    private suspend fun comic(manga: SManga): ComicDto? = client.get(getMangaUrl(manga), rscHeaders, Fetch.XHR)
        .extractNextJs<ComicPageDto>()!!
        .comicDetailData
        .takeIf { it.isNotEmpty() }
        ?.parseAs<ComicDto>()

    private val rscHeaders get() = headersBuilder().set("RSC", "1").build()

    private suspend fun chapters(manga: SManga): List<SChapter> = coroutineScope {
        val episodes = async { allPages<ChapterGroupDto>(manga, "all-chapters").flatMap { it.chapters } }
        val volumes = async { allPages<VolumeDto>(manga, "volumes") }
        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, false)
        val episodeChapters = episodes.await()
            .filter { showLocked || !it.isLocked }
            .map { it.toSChapter(manga.url) }
        val volumeChapters = volumes.await()
            .filter { showLocked || !it.isLocked || it.isTrial }
            .map { it.toSChapter(manga.url, manga.title) }
        (episodeChapters + volumeChapters).reversed()
    }

    private suspend inline fun <reified T> allPages(manga: SManga, path: String): List<T> {
        val items = mutableListOf<T>()
        var page = 1
        do {
            val url = apiUrl.newBuilder().addPathSegment("comic").addPathSegment(manga.url).addPathSegment(path)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("page_size", "500")
                .build()
            val result = client.get(url, Fetch.XHR.from(getMangaUrl(manga)), cacheControl = CacheControl.FORCE_NETWORK)
                .parseAs<ResultDto<PageDto<T>>>().results
            items += result.data
            page++
        } while (result.pagination.hasNextPage)
        return items
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("comic")
        .addPathSegment(manga.url)
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("comic")
        .addPathSegments(chapter.url)
        .addPathSegment("viewer")
        .build()
        .toString()

    // The chapter url in the fragment renews the signed url once it expires
    override suspend fun getPageList(chapter: SChapter): List<Page> = viewer(chapter.url).mapIndexed { index, url ->
        Page(index, imageUrl = url.toHttpUrl().newBuilder().fragment(chapter.url).build().toString())
    }

    // A volume the user doesn't own answers with its preview pages
    private suspend fun viewer(chapter: String): List<String> {
        val url = apiUrl.newBuilder().addPathSegment("comic").addPathSegments(chapter).addPathSegment("viewer").build()
        val chapterUrl = getChapterUrl(SChapter.create().apply { this.url = chapter })
        val response = client.get(url, Fetch.XHR.from(chapterUrl), cacheControl = CacheControl.FORCE_NETWORK, ensureSuccess = false)
        if (response.code == 400) {
            val error = response.parseAs<ErrorDto>().message
            if (error == "not_purchased") return emptyList()
            throw Exception(error)
        }
        return response.parseAs<ResultDto<ViewerDto>>().results.images.map { it.url }
    }

    // Page urls are signed for an hour; the app keeps them in its page list cache
    private fun expiredImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val chapter = request.url.fragment?.takeIf { request.url.host == imageHost }
            ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (response.code != 403) return response
        response.close()
        val path = request.url.encodedPath
        val fresh = resignedPages(chapter).firstOrNull { it.encodedPath == path }
            ?: throw IOException("画像が見つかりません。チャプターを再読み込みしてください")
        return chain.proceed(request.newBuilder().url(fresh).build())
    }

    // Last re-signed chapter, so its other expired pages need no further request
    private var resigned: ResignedPages? = null

    @Synchronized
    private fun resignedPages(chapter: String): List<HttpUrl> {
        resigned?.let { if (it.chapter == chapter && it.fetchedAt.elapsedNow() < RESIGNED_TTL) return it.urls }
        val fetchedAt = TimeSource.Monotonic.markNow()
        val urls = try {
            runBlocking { viewer(chapter) }.map { it.toHttpUrl() }
        } catch (e: Exception) {
            throw e as? IOException ?: IOException(e.message, e)
        }
        resigned = ResignedPages(chapter, fetchedAt, urls)
        return urls
    }

    private class ResignedPages(val chapter: String, val fetchedAt: TimeMark, val urls: List<HttpUrl>)

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = apiUrl.newBuilder().addPathSegment("genres").addQueryParameter("type", "genre_rank").build()
        return client.get(url, Fetch.XHR.from("/ranking")).parseAs<ResultDto<ListDto<GenreDto>>>().results.data.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull(
            ListFilter(),
            data?.parseAs<List<GenreDto>>()?.let(::GenreFilter),
            SearchTypeFilter(),
        ),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "有料エピソードを表示"
            summary = "購入が必要なエピソードと巻を🔒付きで一覧に表示します"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val ALL_GENRE = "01jpa35r8sspwk9zw6gz3je678"
        private val IMAGE_KEY = "57e87c8a4d50b7c3456dbab4ab144b200826e62459039c9915d1e5f5e0bf3a51".decodeHex()
        private const val PREF_SHOW_LOCKED = "show_locked"
        private val RESIGNED_TTL = 10.minutes
    }
}
