package eu.kanade.tachiyomi.extension.en.voyceme

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseGraphQLAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class VoyceMe : HttpSource() {

    // Renamed from "Voyce.Me" to "VoyceMe" as the site uses.
    override val id = 4815322300278778429

    override val name = "VoyceMe"

    override val baseUrl = "https://www.voyce.me"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(GRAPHQL_URL.toHttpUrl(), 1, 1, TimeUnit.SECONDS)
        .rateLimitHost(STATIC_URL.toHttpUrl(), 2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Accept", ACCEPT_ALL)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = graphQLPost(
        GRAPHQL_URL,
        headers,
        query = POPULAR_QUERY,
        variables = PopularQueryVariables(
            offset = (page - 1) * POPULAR_PER_PAGE,
            limit = POPULAR_PER_PAGE,
        ),
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val comicList = response.parseGraphQLAs<VoyceMeSeriesCollection>()
            .series.map(VoyceMeComic::toSManga)
        return MangasPage(comicList, comicList.size == POPULAR_PER_PAGE)
    }

    override fun latestUpdatesRequest(page: Int): Request = graphQLPost(
        GRAPHQL_URL,
        headers,
        query = LATEST_QUERY,
        variables = LatestQueryVariables(
            offset = (page - 1) * POPULAR_PER_PAGE,
            limit = POPULAR_PER_PAGE,
        ),
    )

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = graphQLPost(
        GRAPHQL_URL,
        headers,
        query = SEARCH_QUERY,
        variables = SearchQueryVariables(
            searchTerm = "%$query%",
            offset = (page - 1) * POPULAR_PER_PAGE,
            limit = POPULAR_PER_PAGE,
        ),
    )

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val comicSlug = manga.url.substringAfter("/series/").substringBefore("/")
        return graphQLPost(
            GRAPHQL_URL,
            headersBuilder().set("Referer", baseUrl + manga.url).build(),
            query = DETAILS_QUERY,
            variables = DetailsQueryVariables(slug = comicSlug),
        )
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga = response.parseGraphQLAs<VoyceMeSeriesCollection>().series.first().toSManga()

    override fun chapterListRequest(manga: SManga): Request {
        val comicSlug = manga.url.substringAfter("/series/").substringBefore("/")
        return graphQLPost(
            GRAPHQL_URL,
            headersBuilder().set("Referer", baseUrl + manga.url).build(),
            query = CHAPTERS_QUERY,
            variables = ChaptersQueryVariables(slug = comicSlug),
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val comic = response.parseGraphQLAs<VoyceMeSeriesCollection>().series.first()
        return comic.chapters
            .map { it.toSChapter(comic.slug) }
            .distinctBy(SChapter::name)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/").substringBefore("#").toInt()
        return graphQLPost(
            GRAPHQL_URL,
            headers,
            query = PAGES_QUERY,
            variables = PagesQueryVariables(chapterId = chapterId),
        )
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListParse(response: Response): List<Page> = response.parseGraphQLAs<VoyceChapterImagesCollection>().images
        .mapIndexed { i, page -> Page(i, baseUrl, STATIC_URL + page.image) }

    override fun imageUrlParse(response: Response): String = ""

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
