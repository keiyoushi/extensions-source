package eu.kanade.tachiyomi.extension.tr.juratempest

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl

@Source
abstract class Juratempest : KeiSource() {

    private val apiUrl = "$baseUrl/api/rpc"

    override val supportsLatest = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page != 1) return MangasPage(emptyList(), false)

        val recommendations = rpc<EmptyRequest, List<RecommendationDto>>("recommendation/list", EmptyRequest())
        return MangasPage(recommendations.map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page != 1) return MangasPage(emptyList(), false)

        val releases = rpc<EmptyRequest, List<ReleaseDto>>("release/latest", EmptyRequest())
        val mangas = releases.mapNotNull { it.toSMangaOrNull() }.distinctBy { it.url }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.length < MIN_SEARCH_CHARS) return MangasPage(emptyList(), false)

        val limit = SEARCH_PAGE_SIZE
        val offset = (page - 1) * limit
        val response: SearchResponse = rpc("search/manga", SearchRequest(query, limit, offset))
        val hasNextPage = response.hits.size == limit
        return MangasPage(response.hits.map { it.toSManga() }, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaSlug = manga.url

        val detailsDeferred = if (fetchDetails) {
            async { rpc<SlugRequest, MangaDto>("manga/bySlug", SlugRequest(mangaSlug)) }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { rpc<SlugRequest, List<ChapterDto>>("chapter/byMangaSlug", SlugRequest(mangaSlug)) }
        } else {
            null
        }

        val updatedManga = detailsDeferred?.await()?.toSManga()?.apply { initialized = true } ?: manga
        val updatedChapters = chaptersDeferred?.await()?.map { it.toSChapter(mangaSlug) } ?: chapters

        SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.getOrNull(1) ?: return null
        return rpc<SlugRequest, MangaDto>("manga/bySlug", SlugRequest(slug)).toSManga()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val path = chapter.url.split('/').filter { it.isNotEmpty() }
        val mangaSlug = path.getOrNull(1) ?: throw Exception("Geçersiz bölüm URL'si: ${chapter.url}")
        val chapterSlug = path.getOrNull(2) ?: throw Exception("Geçersiz bölüm URL'si: ${chapter.url}")

        val releases = rpc<ChapterRequest, List<ReleaseDto>>(
            "release/byChapterSlug",
            ChapterRequest(mangaSlug, chapterSlug),
        )
        val pageImages = releases.flatMap { release -> release.pages().map { it.imageUrl() } }

        return pageImages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/explore/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    private suspend inline fun <reified Req, reified Res> rpc(procedure: String, request: Req): Res {
        val url = "$apiUrl/$procedure"
        val response = client.post(url, RpcRequest(request).toJsonRequestBody())
        return response.parseAs<RpcResponse<Res>>().json
    }

    companion object {
        private const val SEARCH_PAGE_SIZE = 20
        private const val MIN_SEARCH_CHARS = 3
    }
}
