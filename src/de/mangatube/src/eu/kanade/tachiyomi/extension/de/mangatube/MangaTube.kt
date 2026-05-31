package eu.kanade.tachiyomi.extension.de.mangatube

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class MangaTube : HttpSource() {

    override val name = "Manga Tube"

    override val baseUrl = "https://manga-tube.me"

    override val lang = "de"

    override val supportsLatest = true

    override val client: OkHttpClient by lazy {
        val baseClient = network.client.newBuilder()
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .build()

        baseClient.newBuilder()
            .addInterceptor(ChallengeInterceptor(baseUrl, headers, baseClient, network.client.cookieJar))
            .build()
    }

    private val apiHeaders: Headers = headersBuilder().add("Accept", "application/json").build()

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/home/top-manga", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<TopMangaResponse>()
        return MangasPage(result.mangas, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * LATEST_PAGE_SIZE
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/home/updates")
            .addQueryParameter("offset", offset.toString())
            .build()
            .toString()
        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestUpdatesResponse>()
        val requestedOffset = response.request.url.queryParameter("offset")?.toIntOrNull() ?: 0
        return MangasPage(result.mangas, requestedOffset < LATEST_PAGE_SIZE * 2)
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/quick-search")
            .addQueryParameter("query", query)
            .build()
            .toString()
        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<QuickSearchResponse>()
        return MangasPage(result.mangas, false)
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/manga/$slug", mangaApiHeaders(slug))
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDetailsResponse>().toSManga()

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/manga/$slug/chapters", mangaApiHeaders(slug))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.pathSegments[2]
        return response.parseAs<MangaChaptersResponse>().toSChapters(slug)
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val (slug, apiPath) = when {
            chapter.url.startsWith("/api/manga/") -> {
                val slug = chapter.url.substringAfter("/api/manga/").substringBefore("/chapter/")
                slug to chapter.url
            }
            chapter.url.startsWith("/series/") -> {
                val slug = chapter.url.substringAfter("/series/").substringBefore("/read/")
                val chapterId = chapter.url.substringAfter("/read/").substringBefore("/").toLong()
                slug to "/api/manga/$slug/chapter/$chapterId"
            }
            else -> error("Unsupported chapter url: ${chapter.url}")
        }
        val requestUrl = "$baseUrl$apiPath"
        return GET(requestUrl, mangaApiHeaders(slug))
    }

    override fun pageListParse(response: Response): List<Page> = try {
        response.parseAs<ChapterDetailsResponse>().pages
            .sortedBy { it.page }
            .mapIndexed { index, page ->
                Page(index, imageUrl = page.imageUrl)
            }
    } catch (e: Exception) {
        emptyList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    companion object {
        private const val LATEST_PAGE_SIZE = 40
    }

    private fun mangaApiHeaders(slug: String): Headers = apiHeaders.newBuilder()
        .add("Referer", "$baseUrl/series/$slug")
        .add("Use-Parameter", "manga_slug")
        .build()
}
