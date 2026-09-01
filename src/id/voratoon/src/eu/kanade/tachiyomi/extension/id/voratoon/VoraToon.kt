package eu.kanade.tachiyomi.extension.id.voratoon

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Source
abstract class VoraToon : KeiSource() {

    private val apiUrl = "https://api.voratoon.com"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        add("Accept", "application/json")
        add("Accept-language", "en-US,en;q=0.9,id;q=0.8")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = parseSeriesListResponse(
        client.get(
            "$apiUrl/series".toHttpUrl().newBuilder()
                .addQueryParameter("includeMeta", "true")
                .addQueryParameter("take", "12")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "totalViews")
                .addQueryParameter("sortOrder", "desc")
                .build(),
        ),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseSeriesListResponse(
        client.get(
            "$apiUrl/series".toHttpUrl().newBuilder()
                .addQueryParameter("includeMeta", "true")
                .addQueryParameter("take", "12")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "latest")
                .addQueryParameter("sortOrder", "desc")
                .build(),
        ),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = parseSeriesListResponse(
        client.get(
            "$apiUrl/series".toHttpUrl().newBuilder()
                .addQueryParameter("includeMeta", "true")
                .addQueryParameter("take", "12")
                .addQueryParameter("page", page.toString())
                .apply {
                    if (query.isNotEmpty()) {
                        addQueryParameter("title", query)
                    }
                    val filterBuilder = StringBuilder()
                    filters.filterIsInstance<UriFilter>().forEach {
                        it.addToFilter(filterBuilder)
                    }
                    if (filterBuilder.isNotEmpty()) {
                        addQueryParameter("filter", filterBuilder.toString())
                    }
                    filters.filterIsInstance<UriQueryFilter>().forEach {
                        it.addToQuery(this)
                    }
                }
                .build(),
        ),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.getSlug(baseUrl)
        val details = if (fetchDetails) async { client.get("$apiUrl/series/$slug") } else null
        val chapterList = if (fetchChapters) async { client.get("$apiUrl/series/$slug/chapters") } else null

        SMangaUpdate(
            manga = details?.await()?.parseAs<SeriesDetailResponse>()?.data?.toSManga() ?: manga,
            chapters = chapterList?.await()?.parseAs<ChapterListResponse>()?.data?.map { it.toSChapter(slug) } ?: chapters,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return try {
            client.get("$apiUrl/series/$slug").parseAs<SeriesDetailResponse>().data.toSManga()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterIndex) = chapter.getSlugAndIndex(baseUrl)
        return client.get("$apiUrl/series/$slug/chapters/$chapterIndex").parseAs<ChapterDetailResponse>().data.toPageList()
    }

    private fun parseSeriesListResponse(response: Response): MangasPage {
        val result = response.parseAs<SeriesListResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.meta?.let { (it.page ?: 0) < (it.lastPage ?: 0) } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<GenreResponse>()?.data?.map { it.toPair() }
        return FilterList(
            buildList {
                add(SortFilter())
                add(SortOrderFilter())
                add(StatusFilter())
                add(FormatFilter())
                add(TypeFilter())
                if (!genres.isNullOrEmpty()) {
                    add(GenreFilter(genres.toTypedArray()))
                }
            },
        )
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .set("Referer", "$baseUrl/")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
