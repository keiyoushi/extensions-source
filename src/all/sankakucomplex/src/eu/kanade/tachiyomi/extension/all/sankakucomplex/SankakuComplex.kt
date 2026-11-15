package eu.kanade.tachiyomi.extension.all.sankakucomplex

import android.util.Base64
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SankakuComplex : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.host == API_HOST }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.DATE)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val tags = listOfNotNull(
            (filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()).tag,
            filters.firstInstanceOrNull<RatingFilter>()?.tag,
            filters.firstInstanceOrNull<LanguageFilter>()?.tag,
            filters.firstInstanceOrNull<TagsFilter>()?.state?.trim()?.ifEmpty { null },
        ).joinToString(" ")

        val url = "$API_URL/poolseriesv2".toHttpUrl().newBuilder()
            .addQueryParameter("lang", "en")
            .addQueryParameter("filledPools", "true")
            .addQueryParameter("offset", "0")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("tags", tags)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("includes[]", "pools")
            .addQueryParameter("exceptStatuses[]", "deleted")
            .apply { if (query.isNotBlank()) addQueryParameter("name", query.trim().replace(WHITESPACE, "_")) }
            .build()

        val data = client.get(url, apiHeaders(), Fetch.XHR).parseAs<BookListDto>()
        val entries = data.series.data.map { it.toSManga() } + data.pools.data.mapNotNull { it.toSManga() }

        return MangasPage(entries, entries.isNotEmpty())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != "www.sankakucomplex.com") return null
        val segments = url.pathSegments
        if (segments.size != 2 || segments[0] !in setOf("books", "series")) return null

        val manga = SManga.create().apply { this.url = "/${segments[0]}/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfterLast('/')

        return if (manga.url.startsWith("/series/")) {
            val loggedIn = accessToken() != null
            val series = fetchSeries(id)
            if (series.books.isEmpty() && series.poolCount > 0) {
                throw Exception(if (loggedIn) BLACKLIST_MESSAGE else LOGIN_MESSAGE)
            }
            SMangaUpdate(series.toDetails(manga, loggedIn), series.toSChapterList())
        } else {
            val pool = fetchPool(id)
            SMangaUpdate(pool.toDetails(manga), listOf(pool.toSChapter()))
        }
    }

    private suspend fun fetchPool(id: String): PoolDto {
        val url = "$API_URL/pools/$id".toHttpUrl().newBuilder()
            .addQueryParameter("lang", "en")
            .addQueryParameter("exceptStatuses[]", "deleted")
            .build()

        return client.get(url, apiHeaders(), Fetch.XHR).parseAs()
    }

    private suspend fun fetchSeries(id: String): SeriesDetailsDto {
        val body = graphQLBody(
            query = SERIES_QUERY,
            operationName = "Series",
            variables = SeriesVariablesDto(id),
        )

        return client.post("$API_URL/graphql", apiHeaders(), Fetch.XHR, body)
            .parseGraphQLAs<SeriesDataDto>()
            .seriesV2
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pool = fetchPool(chapter.url.substringAfterLast('/'))
        val gated = pool.redirectToSignup || pool.posts.size < pool.postCount || pool.posts.any { it.redirectToSignup }

        if (gated && accessToken() == null) {
            throw Exception(LOGIN_MESSAGE)
        }

        return pool.posts
            .sortedBy { it.sequence }
            .mapNotNull { it.fileUrl }
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        RatingFilter(),
        LanguageFilter(),
        TagsFilter(),
    )

    // The web app's API headers: Api-Version 2 gives string ids, Enable-New-Tag-Type the current tag types
    private suspend fun apiHeaders(): Headers = headers.newBuilder()
        .set("Accept", "application/vnd.sankaku.api+json;v=2")
        .set("Api-Version", "2")
        .set("Enable-New-Tag-Type", "true")
        .set("Platform", "web-app")
        .apply { accessToken()?.let { set("Authorization", "Bearer $it") } }
        .build()

    private val tokenMutex = Mutex()

    // The site's WebView login keeps its tokens in cookies; refreshed the way the site does, cleared when refused
    private suspend fun accessToken(): String? = tokenMutex.withLock {
        val site = baseUrl.toHttpUrl()
        val cookies = client.cookieJar.loadForRequest(site)
        val access = cookies.firstOrNull { it.name == "accessToken" }?.value?.takeIf { it.isNotEmpty() }
        if (access != null && !access.isExpired()) return access

        val refresh = cookies.firstOrNull { it.name == "refreshToken" }?.value?.takeIf { it.isNotEmpty() }
            ?: return null

        val response = client.post("$API_URL/auth/token", fetch = Fetch.XHR, body = RefreshDto(refresh).toJsonRequestBody(), ensureSuccess = false)
        val token = when {
            response.code == 401 -> {
                response.close()
                null
            }
            !response.isSuccessful -> {
                response.close()
                throw HttpException(response.code)
            }
            else -> response.parseAs<TokenDto>()
        }

        val now = Clock.System.now()
        fun cookie(name: String, value: String?, ttl: Long?) = Cookie.Builder()
            .hostOnlyDomain(site.host)
            .path("/")
            .name(name)
            .value(value.orEmpty())
            .expiresAt((now + (ttl ?: 0).seconds).toEpochMilliseconds())
            .build()
        client.cookieJar.saveFromResponse(
            site,
            listOf(
                cookie("accessToken", token?.accessToken, token?.accessTokenTtl),
                cookie("refreshToken", token?.refreshToken, token?.refreshTokenTtl),
            ),
        )
        token?.accessToken
    }

    private fun String.isExpired(): Boolean {
        val payload = split('.').getOrNull(1) ?: return true
        val jwt = String(Base64.decode(payload, Base64.URL_SAFE)).parseAs<JwtDto>()
        return jwt.exp <= Clock.System.now().epochSeconds
    }
}

private const val API_URL = "https://sankakuapi.com"
private val API_HOST = API_URL.toHttpUrl().host
private const val PAGE_SIZE = 40
private const val LOGIN_MESSAGE = "Log in via WebView to read this book"
private const val BLACKLIST_MESSAGE = "Every book in this series is hidden by your Sankaku blacklist"
private val WHITESPACE = Regex("""\s+""")

private const val SERIES_QUERY = $$"""
query Series($seriesId: StringOrInt!) {
  seriesV2(id: $seriesId) {
    name
    nameJa
    descriptionEn
    description
    isCompleted
    poolCount
    coverPool { coverPost { previewUrl } }
    tags { name nameEn tagType }
    pools {
      id
      name
      nameEn
      status
      isBlacklisted
      createdAt
      sequenceV2(seriesId: $seriesId)
      tags { name nameEn tagType }
    }
  }
}
"""
