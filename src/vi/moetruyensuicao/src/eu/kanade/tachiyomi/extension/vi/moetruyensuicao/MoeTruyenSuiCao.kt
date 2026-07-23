package eu.kanade.tachiyomi.extension.vi.moetruyensuicao

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.get
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.LinkedHashMap

@Source
abstract class MoeTruyenSuiCao : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(imgxInterceptor())
        rateLimit(3)
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        set("Origin", "https://moetruyen.net")
    }

    // ============================== Popular =======================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/v2/manga/top".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
            .addQueryParameter("sort_by", "views")
            .addQueryParameter("time", "all_time")
            .build()
        return parseMangaList(client.get(url))
    }

    // ============================== Latest ========================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/v2/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
            .addQueryParameter("sort", "updated_at")
            .build()
        return parseMangaList(client.get(url))
    }

    // ============================== Search ========================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/v2/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "20")

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> filter.toApiValue()?.let { addQueryParameter("status", it) }
                    is SortFilter -> addQueryParameter("sort", filter.toApiValue())
                    is GenreFilter -> {
                        val included = filter.state.filterIsInstance<GenreTriStateFilter>()
                            .filter { it.state == Filter.TriState.STATE_INCLUDE }
                            .joinToString(",") { it.genreId.toString() }
                        if (included.isNotBlank()) addQueryParameter("genre", included)

                        val excluded = filter.state.filterIsInstance<GenreTriStateFilter>()
                            .filter { it.state == Filter.TriState.STATE_EXCLUDE }
                            .joinToString(",") { it.genreId.toString() }
                        if (excluded.isNotBlank()) addQueryParameter("genrex", excluded)
                    }
                    else -> {}
                }
            }
        }.build()
        return parseMangaList(client.get(url))
    }

    // ============================== Details =======================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url
        val detailsUrl = "$baseUrl/v2/manga/$mangaId".toHttpUrl().newBuilder()
            .addQueryParameter("include", "genres")
            .build()

        val detailsDeferred = if (fetchDetails) {
            async {
                val result = client.get(detailsUrl).parseAs<ApiResponse<MangaItem>>()
                (result.data ?: throw Exception("Manga not found")).toSManga()
            }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async {
                val result = client.get("$baseUrl/v2/manga/$mangaId/chapters/aggregate")
                    .parseAs<ApiResponse<ChapterListData>>()
                result.data?.chapters
                    ?.filter { it.access == "public" }
                    ?.map { it.toSChapter() }
                    .orEmpty()
            }
        } else {
            null
        }

        SMangaUpdate(
            manga = detailsDeferred?.await() ?: manga,
            chapters = chaptersDeferred?.await() ?: chapters,
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    // ============================== Pages =========================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get("$baseUrl${chapter.url}").parseAs<ApiResponse<ChapterReaderData>>()
        val data = result.data ?: throw Exception("Chapter not found")
        val chapterId = data.chapter.id
        val pageCount = data.pageUrls.size

        if (pageCount == 0) return emptyList()

        return try {
            fetchPagesWithGrants(chapterId, pageCount).ifEmpty {
                data.pageUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
            }
        } catch (_: Exception) {
            data.pageUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
        }
    }

    private suspend fun fetchPagesWithGrants(chapterId: Int, pageCount: Int): List<Page> {
        val pages = mutableListOf<Page>()
        val batchSize = 5

        for (start in 0 until pageCount step batchSize) {
            val end = minOf(start + batchSize, pageCount)
            val indices = (start until end).toList()

            val body = PageAccessRequest(pageIndexes = indices).toJsonRequestBody()
            val url = "$baseUrl/v2/chapters/$chapterId/page-access"
            val accessHeaders = headers.newBuilder()
                .set("Accept", "application/json")
                .build()
            val pageAccess = client.post(url, accessHeaders, body)
                .parseAs<ApiResponse<PageAccessData>>()

            val accessData = pageAccess.data ?: throw Exception("Failed to get page access")

            for (entry in accessData.pages) {
                if (entry.downloadUrl.isNotBlank() && entry.grant != null) {
                    imgxGrants[entry.downloadUrl] = entry
                    pages.add(Page(entry.pageIndex, imageUrl = entry.downloadUrl))
                }
            }
        }

        return pages.sortedBy { it.index }
    }

    private fun imgxInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val entry = imgxGrants.remove(url)

        if (entry?.grant == null) {
            return@Interceptor chain.proceed(request)
        }

        val response = chain.proceed(request)
        val source = response.body.source()

        if (!source.request(14) ||
            source.buffer[0] != 0x49.toByte() || source.buffer[1] != 0x4D.toByte() ||
            source.buffer[2] != 0x47.toByte() || source.buffer[3] != 0x58.toByte()
        ) {
            return@Interceptor response
        }

        val webp = response.body.use {
            ImageDecryptor.decrypt(source.readByteArray(), entry.grant, entry.storageKey)
        }

        response.newBuilder()
            .body(webp.toResponseBody("image/webp".toMediaType()))
            .build()
    }

    private fun DecryptedImage.toResponseBody(mediaType: MediaType): ResponseBody = object : ResponseBody() {
        override fun contentType(): MediaType = mediaType

        override fun contentLength(): Long = size.toLong()

        override fun source(): BufferedSource = ByteArrayInputStream(data, offset, size).source().buffer()
    }

    // ============================== Filters =======================================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/v2/genres")
        .parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data["data"]?.parseAs<List<GenreItem>>().orEmpty()
        val filters = mutableListOf<Filter<*>>()
        if (genres.isNotEmpty()) filters += GenreFilter(genres)
        filters += StatusFilter()
        filters += SortFilter()
        return FilterList(filters)
    }

    // =============================== Related ======================================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = "$baseUrl/v2/manga/${manga.url}/recommendations".toHttpUrl().newBuilder()
            .addQueryParameter("include", "genres")
            .build()
        return client.get(url).parseAs<ListApiResponse<MangaItem>>().data
            .filter { it.id.toString() != manga.url }
            .map { it.toSManga() }
    }

    // ============================== Utilities =====================================

    private fun parseMangaList(response: Response): MangasPage {
        val result = response.parseAs<ListApiResponse<MangaItem>>()
        val mangas = result.data.map { it.toSManga() }
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = result.meta?.pagination?.let { page < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    private val imgxGrants = Collections.synchronizedMap(
        object : LinkedHashMap<String, PageAccessEntry>(IMGX_GRANT_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageAccessEntry>?): Boolean = size > IMGX_GRANT_CACHE_SIZE
        },
    )

    private companion object {
        const val IMGX_GRANT_CACHE_SIZE = 500
    }
}
