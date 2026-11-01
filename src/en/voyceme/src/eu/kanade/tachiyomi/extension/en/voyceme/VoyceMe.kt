package eu.kanade.tachiyomi.extension.en.voyceme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseGraphQLAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class VoyceMe : KeiSource() {
    private val graphqlurlHost by lazy { GRAPHQL_URL.toHttpUrl().host }
    private val staticurlHost by lazy { STATIC_URL.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 1.seconds) { it.host == graphqlurlHost }
        rateLimit(2, 1.seconds) { it.host == staticurlHost }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.post(
            GRAPHQL_URL,
            headers,
            Fetch.XHR,
            graphQLBody(
                query = POPULAR_QUERY,
                variables = PopularQueryVariables(
                    offset = (page - 1) * POPULAR_PER_PAGE,
                    limit = POPULAR_PER_PAGE,
                ),
            ),
        )

        return parseMangaList(response)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val comicList = response.parseGraphQLAs<VoyceMeSeriesCollection>()
            .series.map(VoyceMeComic::toSManga)
        return MangasPage(comicList, comicList.size == POPULAR_PER_PAGE)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.post(
            GRAPHQL_URL,
            headers,
            Fetch.XHR,
            graphQLBody(
                query = LATEST_QUERY,
                variables = LatestQueryVariables(
                    offset = (page - 1) * POPULAR_PER_PAGE,
                    limit = POPULAR_PER_PAGE,
                ),
            ),
        )

        return parseMangaList(response)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.post(
            GRAPHQL_URL,
            headers,
            Fetch.XHR,
            graphQLBody(
                query = SEARCH_QUERY,
                variables = SearchQueryVariables(
                    searchTerm = "%$query%",
                    offset = (page - 1) * POPULAR_PER_PAGE,
                    limit = POPULAR_PER_PAGE,
                ),
            ),
        )

        return parseMangaList(response)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "series") {
            return null
        }

        val manga = SManga.create().apply {
            this.url = "/series/${url.pathSegments[1]}"
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
            }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comicSlug = manga.url.substringAfter("/series/").substringBefore("/")
        val response = client.post(
            GRAPHQL_URL,
            headers,
            Fetch.XHR.from(manga.url),
            graphQLBody(
                query = UPDATES_QUERY,
                variables = ChaptersQueryVariables(slug = comicSlug),
            ),
        )

        val comic = response.parseGraphQLAs<VoyceMeSeriesCollection>().series.first()
        return SMangaUpdate(
            manga = comic.toSManga(),
            chapters = comic.chapters
                .map { it.toSChapter(comic.slug) }
                .distinctBy(SChapter::name),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfterLast("/").substringBefore("#").toInt()
        val response = client.post(
            GRAPHQL_URL,
            headers,
            Fetch.XHR,
            graphQLBody(
                query = PAGES_QUERY,
                variables = PagesQueryVariables(chapterId = chapterId),
            ),
        )

        return response.parseGraphQLAs<VoyceChapterImagesCollection>().images
            .mapIndexed { i, page -> Page(i, baseUrl, STATIC_URL + page.image) }
    }

    override fun getImageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    companion object {
        const val STATIC_URL = "https://dlkfxmdtxtzpb.cloudfront.net/"
        private const val GRAPHQL_URL = "https://hasura.voyce.me/v1/graphql"

        private const val POPULAR_PER_PAGE = 10
    }
}
