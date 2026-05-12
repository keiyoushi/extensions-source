package eu.kanade.tachiyomi.extension.ar.ariatoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response

class AriaToon : HttpSource() {

    override val name = "AriaToon"
    override val baseUrl = "https://ariatoon.com"
    override val lang = "ar"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val apiUrl = "https://api.ariatoon.com/v1"
    private val cdnUrl = "https://api.ariatoon.com/uploads"

    override fun headersBuilder() = super.headersBuilder()
        .add("Accept", "application/json")
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/feed/mangas/popular?page=$page&limit=20", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<PaginatedResponseDto<MangaDto>>()
        val mangas = dto.data.orEmpty().map { it.toSManga(cdnUrl) }

        return MangasPage(mangas, dto.data?.size == 20)
    }

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/mangas?page=$page&limit=20", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val genreId = genreFilter?.toUriPart() ?: ""

        // The API uses different endpoints for text search and genre filtering
        return if (query.isNotEmpty()) {
            GET("$apiUrl/mangas/search?search=$query&page=$page&limit=20", headers)
        } else if (genreId.isNotEmpty()) {
            GET("$apiUrl/mangas/filters/$genreId?page=$page&limit=20&language=ar", headers)
        } else {
            GET("$apiUrl/mangas?page=$page&limit=20", headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/mangas/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<ItemResponseDto<MangaDto>>()
        return dto.data.toSManga(cdnUrl)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/manga/${manga.url}"

    // ============================= Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/mangas/${manga.url}/episodes?direction=desc&publishStatus=published&limit=100&page=1", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<PaginatedResponseDto<ChapterDto>>()
        return dto.data.orEmpty().map { it.toSChapter() }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/series/manga/${chapter.url}"

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = chapter.url.substringBefore("/episodes/")
        val episodeId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/mangas/$mangaId/episodes/$episodeId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ItemResponseDto<EpisodeDetailsDto>>()

        return dto.data.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = "$cdnUrl/$imageUrl")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    override fun getFilterList() = getFilters()
}
