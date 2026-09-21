package eu.kanade.tachiyomi.extension.tr.toontaku

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getBoolean
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Toontaku : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = seriesPage(page, sort = "totalViews,desc")

    // /api/chapters/new is cursor-paginated, so remember the cursor the previous page handed out
    private var latestCursor: String? = null

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) {
            latestCursor = null
        }
        val url = "$apiUrl/chapters/new".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", LATEST_PAGE_SIZE.toString())
            if (page > 1) {
                addQueryParameter("cursor", latestCursor ?: throw Exception("Sayfa bulunamadı, listeyi yenileyin"))
            }
        }.build()
        val data = client.get(url).parseAs<ApiResponse<LatestChaptersDto>>().data
        latestCursor = data.nextCursor
        val mangas = data.items.filter { it.contentKind == "IMAGE_CHAPTER" }.map { it.toSManga() }
        return MangasPage(mangas, !data.isEnd)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query.trim())
            }
            filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.let { addQueryParameter("includeGenres", it) }
            if (filters.firstInstanceOrNull<OnlyFreeFilter>()?.state == true) {
                addQueryParameter("onlyFree", "true")
            }
        }
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "totalViews,desc"
        return seriesPage(page, sort, url)
    }

    private suspend fun seriesPage(
        page: Int,
        sort: String,
        url: HttpUrl.Builder = "$apiUrl/series".toHttpUrl().newBuilder(),
    ): MangasPage {
        url.apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("sortBy", sort.substringBefore(","))
            addQueryParameter("sortOrder", sort.substringAfter(","))
            // the site also hosts light novels (TEXT_CHAPTER)
            addQueryParameter("contentKind", "IMAGE_CHAPTER")
        }
        val data = client.get(url.build()).parseAs<ApiResponse<SeriesListDto>>().data
        return MangasPage(data.series.map { it.toSManga() }, data.page < data.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "seri") {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return fetchDetails(slug).toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/seri/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/seri/${chapter.memo.getString("slug")}/bolum/${chapter.memo.getString("number")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // the chapter endpoint needs the internal series id, which only the details response has, so it is kept in memo
        val updatedManga = if (fetchDetails || manga.memo.getStringOrNull("id") == null) {
            fetchDetails(manga.url).toSManga()
        } else {
            manga
        }
        val chapterList = if (fetchChapters) {
            val url = "$apiUrl/chapters/series/${updatedManga.memo.getString("id")}".toHttpUrl().newBuilder()
                .addQueryParameter("limit", CHAPTER_LIMIT.toString())
                .addQueryParameter("sortBy", "chapterNumber")
                .addQueryParameter("sortOrder", "desc")
                .build()
            client.get(url).parseAs<ApiResponse<ChapterListDto>>().data.chapters.map { it.toSChapter(manga.url) }
        } else {
            chapters
        }
        return SMangaUpdate(updatedManga, chapterList)
    }

    private suspend fun fetchDetails(slug: String): SeriesDetailsDto = client.get("$apiUrl/series/slug/$slug").parseAs<ApiResponse<SeriesDetailsResponse>>().data.series

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // the site answers 403 for locked chapters, which the app's Cloudflare interceptor mistakes for a challenge
        if (chapter.memo.getBoolean("locked")) {
            throw Exception("Bu bölüm kilitli, açmak için sitede oturum açıp satın almanız gerekiyor")
        }
        return client.get("$apiUrl/chapters/${chapter.url}?increment_view=false").parseAs<ApiResponse<ChapterDto>>().data.imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/filters").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<FiltersDto>()?.genres.orEmpty()
        return FilterList(
            buildList {
                add(SortFilter())
                add(TypeFilter())
                add(StatusFilter())
                add(OnlyFreeFilter())
                if (genres.isNotEmpty()) {
                    add(GenreFilter(genres))
                }
            },
        )
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val LATEST_PAGE_SIZE = 60
        private const val CHAPTER_LIMIT = 2000
    }
}
