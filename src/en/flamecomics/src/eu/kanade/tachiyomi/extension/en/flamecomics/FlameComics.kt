package eu.kanade.tachiyomi.extension.en.flamecomics

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class FlameComics : KeiSource() {

    override val supportRelatedMangasBySearch = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::buildIdOutdatedInterceptor)
        .rateLimit(2, 2.seconds) { it.fragment != THUMBNAIL_FRAGMENT }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val series = fetchBrowseSeries().sortedByDescending { it.views }
        return MangasPage(series.mapNotNull { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val series = client.get(dataUrl { addPathSegment("index.json") })
            .parseAs<NextDataDto<LatestDto>>().pageProps.series
        return MangasPage(series.mapNotNull { it.toSManga() }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val normalizedQuery = query.normalizeTitle()
        val series = fetchBrowseSeries().filter { series ->
            (listOf(series.title) + series.altTitles.orEmpty()).any { normalizedQuery in it.normalizeTitle() }
        }
        return MangasPage(series.mapNotNull { it.toSManga() }, false)
    }

    private suspend fun fetchBrowseSeries(): List<SeriesDto> = client.get(dataUrl { addPathSegment("browse.json") })
        .parseAs<NextDataDto<BrowseDto>>().pageProps.series

    private fun String.normalizeTitle() = SPECIAL_CHARS_REGEX.replace(lowercase(), "")

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val seriesId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/series/$seriesId" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesId = getMangaUrl(manga).toHttpUrl().pathSegments.last()
        val seriesPage = client.get(
            dataUrl {
                addPathSegment("series")
                addPathSegment("$seriesId.json")
                addQueryParameter("id", seriesId)
            },
        ).parseAs<NextDataDto<SeriesPageDto>>().pageProps

        return SMangaUpdate(
            manga = seriesPage.series.toSMangaDetails(),
            chapters = seriesPage.chapters.map { it.toSChapter() },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (seriesId, token) = getChapterUrl(chapter).toHttpUrl().pathSegments.drop(1)
        return client.get(
            dataUrl {
                addPathSegment("series")
                addPathSegment(seriesId)
                addPathSegment("$token.json")
                addQueryParameter("id", seriesId)
                addQueryParameter("token", token)
            },
        ).parseAs<NextDataDto<ChapterPageDto>>().pageProps.chapter.toPages()
    }

    // Next.js data routes are keyed by the site's build id, which changes on every deploy.
    @Volatile
    private var buildId: String? = null

    private suspend fun dataUrl(path: HttpUrl.Builder.() -> Unit): HttpUrl {
        val id = buildId
            ?: client.get(baseUrl).asJsoup().extractNextJs<BuildIdDto>()?.buildId?.also { buildId = it }
            ?: throw Exception("Failed to find buildId")

        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("_next")
            .addPathSegment("data")
            .addPathSegment(id)
            .apply(path)
            .build()
    }

    private fun buildIdOutdatedInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url

        if (
            response.code != 404 ||
            url.host != baseUrl.toHttpUrl().host ||
            url.pathSegments.getOrNull(0) != "_next" ||
            url.pathSegments.getOrNull(1) != "data"
        ) {
            return response
        }
        response.close()

        val homeRequest = request.newBuilder()
            .url(baseUrl)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        val newBuildId = chain.proceed(homeRequest).asJsoup().extractNextJs<BuildIdDto>()?.buildId
            ?: throw IOException("Failed to find buildId")
        buildId = newBuildId

        val newUrl = url.newBuilder().setPathSegment(2, newBuildId).build()
        return chain.proceed(request.newBuilder().url(newUrl).build())
    }
}

internal const val THUMBNAIL_FRAGMENT = "thumbnail"

private val SPECIAL_CHARS_REGEX = Regex("""[^A-Za-z0-9 ]""")
