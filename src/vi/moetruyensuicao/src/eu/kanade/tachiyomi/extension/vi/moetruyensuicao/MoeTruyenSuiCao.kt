package eu.kanade.tachiyomi.extension.vi.moetruyensuicao

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class MoeTruyenSuiCao : HttpSource() {

    override val supportsLatest = true

    private val imgxGrants = ConcurrentHashMap<String, PageAccessEntry>()

    override val client = network.client.newBuilder()
        .addInterceptor(imgxInterceptor())
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", "https://moetruyen.net")

    // ============================== Popular =======================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/v2/manga/top".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
            .addQueryParameter("sort_by", "views")
            .addQueryParameter("time", "all_time")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Latest ========================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/v2/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "20")
            .addQueryParameter("sort", "updated_at")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Search ========================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/v2/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "20")

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            val filterList = filters.ifEmpty { getFilterList() }

            filterList.forEach { filter ->
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
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    // ============================== Details =======================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url
        val url = "$baseUrl/v2/manga/$mangaId".toHttpUrl().newBuilder()
            .addQueryParameter("include", "genres")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ApiResponse<MangaItem>>()
        return (result.data ?: throw Exception("Manga not found")).toSManga()
    }

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url
        return "$baseUrl/manga/$mangaId"
    }

    // ============================== Chapters ======================================

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url
        val url = "$baseUrl/v2/manga/$mangaId/chapters/aggregate".toHttpUrl().newBuilder()
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ApiResponse<ChapterListData>>()
        val data = result.data ?: return emptyList()
        return data.chapters.filter { it.access == "public" }.map { it.toSChapter() }
    }

    // ============================== Pages =========================================

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ApiResponse<ChapterReaderData>>()
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

    private fun fetchPagesWithGrants(chapterId: Int, pageCount: Int): List<Page> {
        val pages = mutableListOf<Page>()
        val batchSize = 5

        for (start in 0 until pageCount step batchSize) {
            val end = minOf(start + batchSize, pageCount)
            val indices = (start until end).toList()

            val body = PageAccessRequest(pageIndexes = indices).toJsonRequestBody()
            val url = "$baseUrl/v2/chapters/$chapterId/page-access"

            val request = Request.Builder()
                .url(url)
                .post(body)
                .headers(headers)
                .header("Accept", "application/json")
                .build()

            val pageAccess = client.newCall(request).execute().use {
                it.parseAs<ApiResponse<PageAccessData>>()
            }

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
        val imgxData = response.body.bytes()

        if (imgxData.size <= 13 ||
            imgxData[0] != 0x49.toByte() || imgxData[1] != 0x4D.toByte() ||
            imgxData[2] != 0x47.toByte() || imgxData[3] != 0x58.toByte()
        ) {
            return@Interceptor response.newBuilder()
                .body(imgxData.toResponseBody(response.body.contentType()))
                .build()
        }

        val webp = ImageDecryptor.decrypt(imgxData, entry.grant, entry.storageKey)

        response.newBuilder()
            .body(webp.toResponseBody("image/webp".toMediaType()))
            .build()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters =======================================

    private var genreList: List<GenreItem> = emptyList()

    override fun getFilterList(): FilterList {
        fetchGenreListIfNeeded()
        return if (genreList.isEmpty()) {
            FilterList(
                Filter.Header("Không thể tải danh sách thể loại"),
            )
        } else {
            FilterList(
                GenreFilter(genreList),
                StatusFilter(),
                SortFilter(),
            )
        }
    }

    private fun fetchGenreListIfNeeded() {
        if (genreList.isNotEmpty()) return
        genreList = try {
            val url = "$baseUrl/v2/genres".toHttpUrl()
            client.newCall(GET(url, headers)).execute().use { response ->
                response.parseAs<ListApiResponse<GenreListItem>>().data
                    .map { GenreItem(it.id, it.name) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // ============================== Utilities =====================================

    private fun parseMangaList(response: Response): MangasPage {
        val result = response.parseAs<ListApiResponse<MangaItem>>()
        val mangas = result.data.map { it.toSManga() }
        val page = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = result.meta?.pagination?.let { page < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }
}
