package eu.kanade.tachiyomi.extension.vi.sinhsieusao

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class SinhSieuSao : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)

        val url = "$baseUrl/api/v1/works/top".toHttpUrl().newBuilder()
            .addQueryParameter("period", "monthly")
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<TopWorksResponse>()
        val mangas = result.items.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchWorks(page, "$baseUrl/api/v1/works".toHttpUrl().newBuilder())

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val builder = "$baseUrl/api/v1/works".toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            builder.addQueryParameter("q", query)
        }

        filters.firstInstanceOrNull<SortFilter>()
            ?.takeIf { it.state > 0 }
            ?.let { builder.addQueryParameter("sort", it.toUriPart()) }

        filters.firstInstanceOrNull<KindFilter>()
            ?.takeIf { it.state > 0 }
            ?.let { builder.addQueryParameter("tags[]", it.toUriPart()) }

        filters.firstInstanceOrNull<TagsFilter>()
            ?.state
            ?.filterIsInstance<TagTriStateFilter>()
            ?.forEach {
                when (it.state) {
                    Filter.TriState.STATE_INCLUDE -> builder.addQueryParameter("tags[]", it.slug)
                    Filter.TriState.STATE_EXCLUDE -> builder.addQueryParameter("exclude_tags[]", it.slug)
                }
            }

        return fetchWorks(page, builder)
    }

    private suspend fun fetchWorks(page: Int, builder: HttpUrl.Builder): MangasPage {
        val url = builder
            .addQueryParameter("items", "24")
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<WorksResponse>()
        val mangas = result.items.orEmpty().map { it.toSManga() }
        val pagy = result.meta.pagy
        return MangasPage(mangas, pagy.page < pagy.pages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "works") return null

        val workId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return fetchWork(workId).toSManga().apply { initialized = true }
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val workId = manga.url.toIntOrNull()
            ?: return SMangaUpdate(manga = manga, chapters = chapters)

        val work = fetchWork(workId)
        val updatedChapters = if (fetchChapters) {
            when (work.kind) {
                "album" -> fetchAlbumChapter(work)
                else -> fetchMangaChapters(work.workableId ?: work.id, work.id)
            }
        } else {
            chapters
        }

        return SMangaUpdate(
            manga = if (fetchDetails) work.toSManga() else manga.apply { memo = work.toMemo() },
            chapters = updatedChapters,
        )
    }

    private suspend fun fetchWork(workId: Int): WorkDto = client.get("$baseUrl/api/v1/works/$workId", Fetch.XHR).parseAs()

    private suspend fun fetchMangaChapters(mangaId: Int, workId: Int): List<SChapter> = client.get("$baseUrl/api/v1/mangas/$mangaId", Fetch.XHR)
        .parseAs<MangaDto>()
        .toSChapters(workId)

    private suspend fun fetchAlbumChapter(work: WorkDto): List<SChapter> {
        val albumId = work.workableId ?: work.id
        return listOf(fetchAlbum(albumId, limit = 1).toSChapter(work.id))
    }

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val albumId = chapter.memo["albumId"]?.stringOrNull?.toIntOrNull()
        if (albumId != null) {
            return fetchAllAlbumPhotos(albumId).mapIndexed { index, photo ->
                Page(index, imageUrl = photo.imageUrl)
            }
        }

        val chapterId = chapter.memo["chapterId"]?.stringOrNull ?: chapter.url.substringAfterLast('/')
        return client.get("$baseUrl/api/v1/chapters/$chapterId", Fetch.XHR)
            .parseAs<ChapterDetailDto>()
            .toPages()
    }

    private suspend fun fetchAlbum(albumId: Int, limit: Int = 100, offset: Int = 0): AlbumResponse {
        val url = "$baseUrl/api/v1/albums/$albumId".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("photos_sort", "oldest")
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    private suspend fun fetchAllAlbumPhotos(albumId: Int): List<PhotoDto> = coroutineScope {
        val firstPage = fetchAlbum(albumId)
        val remainingOffsets = (firstPage.photosList.size until firstPage.photosTotal step 100).toList()
        val remainingPages = remainingOffsets
            .map { offset -> async { fetchAlbum(albumId, offset = offset).photosList } }
            .awaitAll()

        (listOf(firstPage.photosList) + remainingPages)
            .flatten()
            .sortedBy { it.order }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/works/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Filters =============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val firstPage = fetchTagsPage(1)
        val remainingPages = (2..firstPage.meta.pagy.pages)
            .map { page -> async { fetchTagsPage(page) } }
            .awaitAll()
        val tags = (listOf(firstPage) + remainingPages)
            .flatMap { result -> result.items.orEmpty().map { GenreItem(it.name, it.slug) } }

        tags.toJsonElement()
    }

    private suspend fun fetchTagsPage(page: Int): TagsResponse {
        val url = "$baseUrl/api/v1/tags".toHttpUrl().newBuilder()
            .addQueryParameter("items", "100")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreItem>>().orEmpty()

        return FilterList(
            buildList {
                add(KindFilter())
                add(SortFilter())
                if (genres.isNotEmpty()) add(TagsFilter(genres))
            },
        )
    }
}
