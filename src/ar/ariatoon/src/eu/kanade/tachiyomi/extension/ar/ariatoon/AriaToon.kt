package eu.kanade.tachiyomi.extension.ar.ariatoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class AriaToon : KeiSource() {

    private val apiUrl = "https://api.ariatoon.com/v1"
    private val cdnUrl = "https://api.ariatoon.com/uploads"

    override fun Headers.Builder.configureHeaders() = add("Accept", "application/json")

    private fun Response.toMangasPage(): MangasPage {
        val dto = this.parseAs<PaginatedResponseDto<MangaDto>>()
        val mangas = dto.data.orEmpty().map { it.toSManga(cdnUrl) }

        return MangasPage(mangas, dto.data?.size == 20)
    }

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$apiUrl/feed/mangas/popular?page=$page&limit=20")
        return response.toMangasPage()
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$apiUrl/mangas?page=$page&limit=20")
        return response.toMangasPage()
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val genreId = genreFilter?.toUriPart() ?: ""

        // The API uses different endpoints for text search and genre filtering
        val url = if (query.isNotEmpty()) {
            "$apiUrl/mangas/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("page", page.toString())
                addQueryParameter("limit", "20")
                addQueryParameter("search", query)
            }.build().toString()
        } else if (genreId.isNotEmpty()) {
            "$apiUrl/mangas/filters/$genreId?page=$page&limit=20&language=ar"
        } else {
            "$apiUrl/mangas?page=$page&limit=20"
        }
        return client.get(url).toMangasPage()
    }

    // ==================== Details & Chapters ====================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (!fetchDetails) return@async manga
            val response = client.get("$apiUrl/mangas/${manga.url}")
            response.parseAs<ItemResponseDto<MangaDto>>().data.toSManga(cdnUrl)
        }
        val chaptersDeferred = async {
            if (!fetchChapters) return@async chapters
            val response = client.get(
                "$apiUrl/mangas/${manga.url}/episodes?direction=desc&publishStatus=published&limit=100&page=1",
            )
            val dto = response.parseAs<PaginatedResponseDto<ChapterDto>>()
            dto.data.orEmpty().map { it.toSChapter() }
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    // =============================== Pages ===============================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/manga/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.url.substringBefore("/episodes/")
        val episodeId = chapter.url.substringAfterLast("/")
        val response = client.get("$apiUrl/mangas/$mangaId/episodes/$episodeId")
        val dto = response.parseAs<ItemResponseDto<EpisodeDetailsDto>>()

        return dto.data.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = "$cdnUrl/$imageUrl")
        }
    }

    // ============================== Filters ==============================
    override fun getFilterList(data: JsonElement?) = getFilters()
}
