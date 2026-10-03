package eu.kanade.tachiyomi.extension.ja.jumptoon

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.GraphQLErrorInterceptor
import keiyoushi.utils.GraphQLException
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseAs
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.URLDecoder
import kotlin.time.Duration.Companion.minutes

@Source
abstract class JumpToon :
    KeiSource(),
    ConfigurableSource {
    private val apiUrl get() = "https://api.g.${baseUrl.toHttpUrl().host}/query"
    private val preferences by getPreferencesLazy()
    private val tokenMutex = Mutex()

    private var token: String? = null
    private var tokenSource: String? = null
    private var tokenExpires = 0L

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(GraphQLErrorInterceptor())
        addInterceptor(ImageInterceptor())
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.post(
            url = apiUrl,
            body = graphQLBody(
                query = RANKING_QUERY,
                operationName = "SeriesOverallRankingPage",
            ),
        ).parseGraphQLAs<RankingResponse>()
        val mangas = result.rankingFeedV2.seriesList.map { it.series.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.post(
            url = apiUrl,
            body = graphQLBody(
                query = LATEST_QUERY,
                operationName = "TopPageData",
            ),
        ).parseGraphQLAs<LatestResponse>().dailyUpdatedSeriesFeedList
        val mangas = result.flatMap { it.rankedSeriesList }.map { it.series.toSManga() }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val result = client.post(
            url = apiUrl,
            body = graphQLBody(
                query = SEARCH_QUERY,
                operationName = "SearchSeries",
                variables = SearchVariables(query, (page - 1) * 24, 24),
            ),
        ).parseGraphQLAs<SearchResponse>().searchSeries
        val mangas = result.seriesList.flatMap { listOfNotNull(it, it.pairedSeries) }.map { it.toSManga() }
        val hasNextPage = page * 24 < result.totalCount
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val apiHeaders = apiHeaders()
        val result = client.post(
            url = apiUrl,
            headers = apiHeaders,
            body = graphQLBody(
                query = DETAILS_QUERY,
                operationName = "SeriesPageData",
                variables = SeriesVariables(manga.url, apiHeaders["Authorization"] != null),
            ),
        ).parseGraphQLAs<DetailsResponse>()

        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val episodes = result.seriesEpisodeList.edges
            .filter { !hideLocked || !it.node.isLocked }
            .map { it.node.toSChapter() }

        val volumes = result.seriesComicsList.edges
            .filter { !hideLocked || !it.node.isLocked }
            .map { it.node.toSChapter() }

        val chapterList = episodes + volumes
        return SMangaUpdate(
            result.series.toSManga(),
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val path = if (chapter.memo["type"] != null) "comics" else "episodes"
        return "$baseUrl/series/${chapter.memo["seriesId"]!!.string}/$path/${chapter.url}/"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (query, operationName) = when (chapter.memo["type"]?.stringOrNull) {
            "comics" -> COMICS_QUERY to "SeriesComicsViewerContent"
            "preview" -> PREVIEW_QUERY to "SeriesComicsTrialPreviewContent"
            else -> CONTENT_QUERY to "SeriesEpisodePageData"
        }

        val content = try {
            client.post(
                url = apiUrl,
                headers = apiHeaders(),
                body = graphQLBody(
                    query = query,
                    operationName = operationName,
                    variables = ContentVariables(chapter.memo["seriesId"]!!.string, chapter.url),
                ),
            ).parseGraphQLAs<ContentResponse>().content
        } catch (e: GraphQLException) {
            if (e.message != "RequiredPurchase") throw e
            throw Exception("Log in via WebView and rent or purchase this chapter to read.")
        }

        val seed = "${content.seriesId}:${content.number}".sumOf { it.code }
        return content.pageList.mapIndexed { i, page ->
            Page(i, imageUrl = "${page.imageUrl}#${content.scrambleAlgorithmType}:$seed:${page.width}")
        }
    }

    private suspend fun apiHeaders(): Headers {
        val token = getToken() ?: return headers
        val deviceId = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).firstOrNull { it.name == "deviceId" }?.value
        return headersBuilder()
            .set("Authorization", "Bearer $token")
            .apply {
                if (deviceId != null) {
                    set("X-Client-Device-Id", deviceId)
                }
            }
            .build()
    }

    private suspend fun getToken(): String? = tokenMutex.withLock {
        val cookie = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).firstOrNull { it.name == "session" } ?: return null
        val refreshToken = URLDecoder.decode(cookie.value, "UTF-8").parseAs<SessionCookie>().refreshToken
        if (refreshToken == tokenSource && System.currentTimeMillis() < tokenExpires) return token

        val url = "https://securetoken.googleapis.com/v1/token".toHttpUrl().newBuilder()
            .addQueryParameter("key", LOGIN_KEY)
            .build()

        token = client.post(url, RefreshRequestBody("refresh_token", refreshToken).toJsonRequestBody()).parseAs<TokenResponse>().idToken
        tokenSource = refreshToken
        tokenExpires = System.currentTimeMillis() + 50.minutes.inWholeMilliseconds
        token
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val LOGIN_KEY = "AIzaSyBF0YFCH2gJ67rYZ-j4pBjLJ4GiN-nrsI0"
    }
}
