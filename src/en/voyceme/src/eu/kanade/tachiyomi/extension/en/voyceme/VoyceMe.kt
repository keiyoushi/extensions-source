package eu.kanade.tachiyomi.extension.en.voyceme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseGraphQLAs
import okhttp3.Headers
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

    override fun Headers.Builder.configureHeaders() = apply {
        add("Accept", ACCEPT_ALL)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.newCall(
            graphQLPost(
                GRAPHQL_URL,
                headers,
                query = POPULAR_QUERY,
                variables = PopularQueryVariables(
                    offset = (page - 1) * POPULAR_PER_PAGE,
                    limit = POPULAR_PER_PAGE,
                ),
            ),
        ).awaitSuccess()

        return parseMangaList(response)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val comicList = response.parseGraphQLAs<VoyceMeSeriesCollection>()
            .series.map(VoyceMeComic::toSManga)
        return MangasPage(comicList, comicList.size == POPULAR_PER_PAGE)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.newCall(
            graphQLPost(
                GRAPHQL_URL,
                headers,
                query = LATEST_QUERY,
                variables = LatestQueryVariables(
                    offset = (page - 1) * POPULAR_PER_PAGE,
                    limit = POPULAR_PER_PAGE,
                ),
            ),
        ).awaitSuccess()

        return parseMangaList(response)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.newCall(
            graphQLPost(
                GRAPHQL_URL,
                headers,
                query = SEARCH_QUERY,
                variables = SearchQueryVariables(
                    searchTerm = "%$query%",
                    offset = (page - 1) * POPULAR_PER_PAGE,
                    limit = POPULAR_PER_PAGE,
                ),
            ),
        ).awaitSuccess()

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
        val response = client.newCall(
            graphQLPost(
                GRAPHQL_URL,
                headersBuilder().set("Referer", baseUrl + manga.url).build(),
                query = UPDATES_QUERY,
                variables = ChaptersQueryVariables(slug = comicSlug),
            ),
        ).awaitSuccess()

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
        val response = client.newCall(
            graphQLPost(
                GRAPHQL_URL,
                headers,
                query = PAGES_QUERY,
                variables = PagesQueryVariables(chapterId = chapterId),
            ),
        ).awaitSuccess()

        return response.parseGraphQLAs<VoyceChapterImagesCollection>().images
            .mapIndexed { i, page -> Page(i, baseUrl, STATIC_URL + page.image) }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    companion object {
        private const val ACCEPT_ALL = "*/*"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"

        const val STATIC_URL = "https://dlkfxmdtxtzpb.cloudfront.net/"
        private const val GRAPHQL_URL = "https://graphql.voyce.me/v1/graphql"

        private const val POPULAR_PER_PAGE = 10
    }
}
