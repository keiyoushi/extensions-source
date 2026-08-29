package eu.kanade.tachiyomi.extension.vi.sinhsieusao

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class SinhSieuSao : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    override fun Headers.Builder.configureHeaders() = add("Accept", "application/json")

    // ============================== Popular =======================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/api/v1/works/top".toHttpUrl().newBuilder()
            .addQueryParameter("period", "monthly")
            .build()
        val result = client.get(url).parseAs<TopWorksResponse>()
        val mangas = result.items.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ========================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/v1/works".toHttpUrl().newBuilder()
            .addQueryParameter("items", "20")
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url).parseAs<WorksResponse>()
        val mangas = result.items.map { it.toSManga(baseUrl) }
        val hasNextPage = result.meta.pagy.page < result.meta.pagy.pages
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ========================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val kindFilter = filters.firstInstanceOrNull<KindFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val tagsFilter = filters.firstInstanceOrNull<TagsFilter>()

        val url = "$baseUrl/api/v1/works".toHttpUrl().newBuilder()
            .addQueryParameter("items", "20")
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        kindFilter?.let {
            if (it.state > 0) {
                url.addQueryParameter("kind", it.toUriPart())
            }
        }

        sortFilter?.let {
            if (it.state > 0) {
                url.addQueryParameter("sort", it.toUriPart())
            }
        }

        tagsFilter?.let { filter ->
            val includedSlugs = filter.state
                .filterIsInstance<TagTriStateFilter>()
                .filter { it.state == Filter.TriState.STATE_INCLUDE }
                .map { it.slug }
            for (slug in includedSlugs) {
                url.addQueryParameter("tag", slug)
            }

            val excludedSlugs = filter.state
                .filterIsInstance<TagTriStateFilter>()
                .filter { it.state == Filter.TriState.STATE_EXCLUDE }
                .map { it.slug }
            for (slug in excludedSlugs) {
                url.addQueryParameter("exclude_tag", slug)
            }
        }

        val result = client.get(url.build()).parseAs<WorksResponse>()
        val mangas = result.items.map { it.toSManga(baseUrl) }
        val hasNextPage = result.meta.pagy.page < result.meta.pagy.pages
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "works") return null

        val workId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return fetchWork(workId.toString()).toSManga(baseUrl).apply { initialized = true }
    }

    // ============================== Details =======================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val work = fetchWork(manga.url)
        val updatedChapters = if (fetchChapters) {
            when (work.kind) {
                "album" -> fetchAlbumChapters(work.workableId ?: work.id, work.id)
                else -> fetchMangaChapters(work.workableId ?: work.id, work.id)
            }
        } else {
            chapters
        }

        return SMangaUpdate(work.toSManga(baseUrl), updatedChapters)
    }

    private suspend fun fetchWork(workId: String): WorkDto = client.get("$baseUrl/api/v1/works/$workId").parseAs()

    private suspend fun fetchMangaChapters(mangaId: Int, workId: Int): List<SChapter> = client.get("$baseUrl/api/v1/mangas/$mangaId")
        .parseAs<MangaDto>()
        .chapters
        .filter { it.processingStatus == "processed" }
        .sortedByDescending { it.order }
        .map { it.toSChapter(workId) }

    private suspend fun fetchAlbumChapters(albumId: Int, workId: Int): List<SChapter> {
        val album = client.get(albumUrl(albumId)).parseAs<AlbumResponse>()
        return listOf(album.toSChapter(workId))
    }

    // ============================== Pages =========================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith("album:")) {
            val albumId = chapter.url.removePrefix("album:").substringBefore(':').toInt()
            val album = client.get(albumUrl(albumId)).parseAs<AlbumResponse>()
            return album.photos
                .sortedBy { it.order }
                .mapIndexed { index, photo ->
                    val imageUrl = baseUrl + photo.imageUrl
                    Page(index, imageUrl = imageUrl)
                }
        }

        val chapterId = chapter.url.substringAfterLast('/')
        val chapterDetails = client.get("$baseUrl/api/v1/chapters/$chapterId")
            .parseAs<ChapterDetailDto>()
        return chapterDetails.pages
            .sortedBy { it.order }
            .mapIndexed { index, page ->
                val imageUrl = baseUrl + page.imageUrl
                Page(index, imageUrl = imageUrl)
            }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/works/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = when {
        chapter.url.startsWith("album:") && chapter.url.count { it == ':' } == 2 ->
            "$baseUrl/works/${chapter.url.substringAfterLast(':')}"
        chapter.url.startsWith("album:") -> baseUrl
        chapter.url.startsWith("/works/") -> baseUrl + chapter.url
        else -> "$baseUrl/chapters/${chapter.url}"
    }

    // ============================== Filters =======================================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val firstPage = fetchTagsPage(1)
        val remainingPages = (2..firstPage.meta.pagy.pages)
            .map { page -> async { fetchTagsPage(page) } }
            .awaitAll()
        val tags = (listOf(firstPage) + remainingPages)
            .flatMap { result -> result.items.map { GenreItem(it.name, it.slug) } }

        tags.toJsonElement()
    }

    private suspend fun fetchTagsPage(page: Int): TagsResponse {
        val url = "$baseUrl/api/v1/tags".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).parseAs()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreItem>>().orEmpty()

        return if (genres.isEmpty()) {
            FilterList(KindFilter(), SortFilter())
        } else {
            FilterList(
                KindFilter(),
                TagsFilter(genres),
                SortFilter(),
            )
        }
    }

    private fun albumUrl(albumId: Int) = "$baseUrl/api/v1/albums/$albumId?limit=200&offset=0&photos_sort=oldest"
}
