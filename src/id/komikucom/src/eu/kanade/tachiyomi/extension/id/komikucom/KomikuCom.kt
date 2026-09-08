package eu.kanade.tachiyomi.extension.id.komikucom

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
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class KomikuCom : KeiSource() {

    private val apiUrl = "https://01.komiku.asia/api/v2"

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        add("Accept", "application/json, text/plain, */*")
        add("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
        add("Sec-Fetch-Dest", "empty")
        add("Sec-Fetch-Mode", "cors")
        add("Sec-Fetch-Site", "same-origin")
    }

    // ------------------------- Browse (Popular) -------------------------

    override suspend fun getPopularManga(page: Int): MangasPage = parseComicListResponse(
        client.get(
            "$apiUrl/comics".toHttpUrl().newBuilder()
                .addQueryParameter("sort", "popular")
                .addQueryParameter("page", page.toString())
                .build(),
        ),
    )

    // ------------------------- Latest -------------------------

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseComicListResponse(
        client.get(
            "$apiUrl/comics".toHttpUrl().newBuilder()
                .addQueryParameter("sort", "update")
                .addQueryParameter("page", page.toString())
                .build(),
        ),
    )

    private fun parseComicListResponse(response: Response): MangasPage {
        val result = response.parseAs<ComicListResponse>()
        val mangas = result.items?.map { it.toSManga() } ?: emptyList()
        val hasNextPage = (result.page ?: 0) < (result.totalPages ?: 0)
        return MangasPage(mangas, hasNextPage)
    }

    // ------------------------- Search -------------------------

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // /comics/search requires `q`; when browsing without a query, use /comics with filters instead
        val endpoint = if (query.isEmpty()) "$apiUrl/comics" else "$apiUrl/comics/search"
        val url = endpoint.toHttpUrl().newBuilder()
            .apply {
                if (query.isNotEmpty()) {
                    addQueryParameter("q", query)
                }
                filters.filterIsInstance<UriQueryFilter>().forEach { it.addToQuery(this) }
                addQueryParameter("page", page.toString())
            }
            .build()
        val response = client.get(url)
        return if (query.isEmpty()) {
            parseComicListResponse(response)
        } else {
            val mangas = response.parseAs<List<ComicDto>>().map { it.toSManga() }
            MangasPage(mangas, mangas.isNotEmpty())
        }
    }

    // ------------------------- Filters -------------------------

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/comics/filters").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val dto = data?.parseAs<FilterResponse>() ?: return FilterList()
        return FilterList(
            buildList {
                add(SortFilter(dto.sorts?.map { it.toPair() }?.toTypedArray() ?: emptyArray()))
                add(StatusFilter(dto.statuses?.filter { it != "ngoing" }?.map { if (it == "Semua") "Semua" to "" else it to it }?.toTypedArray() ?: emptyArray()))
                add(TypeFilter(dto.types?.map { if (it == "Semua") "Semua" to "" else it to it }?.toTypedArray() ?: emptyArray()))
                add(GenreFilter(dto.genres?.map { it to it }?.toTypedArray() ?: emptyArray()))
            },
        )
    }

    // ------------------------- Detail + Chapters -------------------------

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val comicId = manga.memo["id"]!!.string
        val slug = manga.url.substringAfter("/manga/")
        val details = if (fetchDetails) async { client.get("$apiUrl/comics/$slug") } else null
        val chapterList = if (fetchChapters) async { client.get("$apiUrl/comics/$comicId/chapters") } else null

        val comicDto = details?.await()?.parseAs<ComicDto>()
        val chapterDtos = chapterList?.await()?.parseAs<List<ChapterDto>>()
        SMangaUpdate(
            manga = comicDto?.toSManga() ?: manga,
            chapters = chapterDtos?.map { it.toSChapter(comicId.toInt()) } ?: chapters,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return try {
            client.get("$apiUrl/comics/$slug").parseAs<ComicDto>().toSManga()
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.split("/").filter { it.isNotEmpty() }
        val comicId = segments.getOrNull(0)
        val chapterId = segments.getOrNull(1)
        return client.get("$apiUrl/comics/$comicId/chapters/id/$chapterId")
            .parseAs<ChapterDetailDto>()
            .toPageList()
    }
}
