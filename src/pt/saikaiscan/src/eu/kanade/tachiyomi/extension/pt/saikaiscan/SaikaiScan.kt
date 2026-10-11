package eu.kanade.tachiyomi.extension.pt.saikaiscan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SaikaiScan : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$host"
    private val storageUrl get() = "https://s3-beta.$host"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 2.seconds) { it.host == apiUrl.toHttpUrl().host }
        rateLimit(1, 1.seconds) { it.host == storageUrl.toHttpUrl().host }
    }

    val apiHeaders get() = headersBuilder()
        .add("Accept", ACCEPT_JSON)
        .build()

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val apiEndpointUrl = "$apiUrl/api/lancamentos".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("relationships", "language,type,format,latestReleases.separator")
            .build()

        return parseSearch(client.get(apiEndpointUrl, apiHeaders))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.count(String::isNotBlank) < 2) return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val apiEndpointUrl = "$apiUrl/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("q", query)
            .addQueryParameter("sortProperty", "pageViews")
            .addQueryParameter("sortDirection", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE)
            .addQueryParameter("relationships", "language,type,format")

        filters.filterIsInstance<UrlQueryFilter>()
            .forEach { it.addQueryParameter(apiEndpointUrl) }

        return parseSearch(
            client.get(apiEndpointUrl.build(), apiHeaders),
        )
    }

    private fun parseSearch(response: Response): MangasPage {
        val result = response.parseAs<PaginatedStories>()
        val mangaList = result.data!!.map { it.toSManga(storageUrl) }
        return MangasPage(mangaList, result.hasNextPage)
    }

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val storySlug = manga.url.substringAfterLast("/")

        val apiEndpointUrl = "$apiUrl/api/stories".toHttpUrl().newBuilder()
            .addQueryParameter("format", COMIC_FORMAT_ID)
            .addQueryParameter("slug", storySlug)
            .addQueryParameter("per_page", "1")
            .addQueryParameter("relationships", "releases,language,type,format,artists,status")
            .build()

        val result = client.get(
            apiEndpointUrl,
            apiHeaders,
        ).parseAs<PaginatedStories>()

        val story = result.data!![0]

        val updatedChapters = story.releases
            .filter { it.isActive == 1 }
            .map { it.toSChapter(story.slug) }
            .sortedByDescending(SChapter::chapter_number)

        return SMangaUpdate(story.toSManga(storageUrl), updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val releaseId = chapter.url
            .substringBeforeLast("/")
            .substringAfterLast("/")

        val apiEndpointUrl = "$apiUrl/api/releases/$releaseId".toHttpUrl().newBuilder()
            .addQueryParameter("relationships", "releaseImages")
            .build()

        val result = client.get(
            apiEndpointUrl,
            apiHeaders,
        ).parseAs<ReleaseResult>()

        return result.data?.releaseImages.orEmpty().mapIndexed { i, obj ->
            Page(i, imageUrl = "$storageUrl/${obj.image}")
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .add("Accept", ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get(
        "$apiUrl/api/genres",
    ).parseAs<GenresResult>().data.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOf(
            CountryFilter(),
            StatusFilter(),
            SortByFilter(),
        ) + buildList {
            val genres = data?.parseAs<List<GenreDto>>().orEmpty()
            if (genres.isNotEmpty()) add(GenreFilter(genres.map { Genre(it.name, it.id) }))
        },
    )

    companion object {
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_JSON = "application/json, text/plain, */*"
        private const val COMIC_FORMAT_ID = "2"
        private const val PER_PAGE = "12"
    }
}
