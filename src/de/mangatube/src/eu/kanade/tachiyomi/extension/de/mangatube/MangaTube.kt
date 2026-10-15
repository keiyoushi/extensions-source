package eu.kanade.tachiyomi.extension.de.mangatube

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.minutes

@Source
abstract class MangaTube : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        connectTimeout(1.minutes)
        readTimeout(1.minutes)
        writeTimeout(1.minutes)

        val baseClient = build()
        addInterceptor(ChallengeInterceptor(baseUrl, headers, baseClient, baseClient.cookieJar))
    }

    private val apiHeaders: Headers get() = headers.newBuilder().add("Accept", "application/json").build()

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$baseUrl/api/home/top-manga", apiHeaders).parseAs<TopMangaResponse>()
        return MangasPage(result.mangas, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * LATEST_PAGE_SIZE
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/home/updates")
            .addQueryParameter("offset", offset.toString())
            .build()
        val result = client.get(url, apiHeaders).parseAs<LatestUpdatesResponse>()
        return MangasPage(result.mangas, offset < LATEST_PAGE_SIZE * 2)
    }

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/quick-search")
            .addQueryParameter("query", query)
            .build()
        val result = client.get(url, apiHeaders).parseAs<QuickSearchResponse>()
        return MangasPage(result.mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null

        return fetchMangaDetails(slug)
    }

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.substringAfterLast("/")

        val details = async { if (fetchDetails) fetchMangaDetails(slug) else manga }
        val chapterList = async {
            if (fetchChapters) {
                client.get("$baseUrl/api/manga/$slug/chapters", mangaApiHeaders(slug))
                    .parseAs<MangaChaptersResponse>()
                    .toSChapters(slug)
            } else {
                chapters
            }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(slug: String): SManga = client.get("$baseUrl/api/manga/$slug", mangaApiHeaders(slug))
        .parseAs<MangaDetailsResponse>()
        .toSManga()

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
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
        val response = client.get("$baseUrl$apiPath", mangaApiHeaders(slug))

        return try {
            response.parseAs<ChapterDetailsResponse>().pages
                .sortedBy { it.page }
                .mapIndexed { index, page ->
                    Page(index, imageUrl = page.imageUrl)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val LATEST_PAGE_SIZE = 40
    }

    private fun mangaApiHeaders(slug: String): Headers = apiHeaders.newBuilder()
        .set("Referer", "$baseUrl/series/$slug")
        .add("Use-Parameter", "manga_slug")
        .build()
}
