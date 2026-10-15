package eu.kanade.tachiyomi.extension.pt.mangaflix

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
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaFlix : KeiSource() {

    private val apiUrl = "https://api.mangaflix.net/v1"

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    private var genresList: List<Genre> = emptyList()

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/browse").parseAs<BrowseResponseDto>()
        val popularSection = result.data.firstOrNull { it.key == "most-read" }

        val mangas = popularSection?.items?.let { itemsElement ->
            itemsElement.parseAs<List<MangaDto>>().map { it.toSManga() }
        } ?: emptyList()

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.get("$apiUrl/latest-releases?selected_language=pt-br").parseAs<LatestResponseDto>()
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val result = client.get("$apiUrl/search/mangas?query=$query&selected_language=pt-br").parseAs<SearchResponseDto>()
            val mangas = result.data.works.map { item ->
                SManga.create().apply {
                    title = item.name
                    thumbnail_url = item.poster?.default_url
                    description = item.description
                    genre = item.genres.mapNotNull { id -> genresList.find { it.id == id }?.name }.joinToString()
                    url = "/br/manga/${item._id}"
                }
            }
            return MangasPage(mangas, false)
        }

        val genreId = filters.firstInstanceOrNull<GenreFilter>()?.state?.firstOrNull { it.state }?.id
            ?: return getLatestUpdates(page)

        val offset = (page - 1) * 20
        val result = client.get("$apiUrl/genres/$genreId/mangas/?offset=$offset&limit=20&include_adult=true").parseAs<GenreResponseDto>()
        val mangas = result.data.map { it.toSManga() }
        val totalItems = result.metadata?.total ?: 0

        return MangasPage(mangas, offset + result.data.size < totalItems)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres?include_adult=true&selected_language=pt-br")
        .parseAs<GenreListResponseDto>()
        .data
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        data ?: return FilterList()
        genresList = data.parseAs<List<GenreItemDto>>().map { Genre(it.name, it._id) }
        return FilterList(GenreFilter(genresList))
    }

    // =========================== Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = manga.url.substringAfterLast("/")
        val details = client.get("$apiUrl/mangas/$id").parseAs<MangaDetailsResponseDto>().data

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = details.name
            thumbnail_url = details.poster?.default_url
            description = details.description
            genre = details.genres.joinToString { it.name }
            author = details.chapters.firstOrNull()?.owners?.firstOrNull()?.name
            status = SManga.UNKNOWN
        }

        val chapterList = details.chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.number.toFloatOrNull() ?: 0F
                name = chapter.name?.ifBlank { null } ?: "Capítulo ${chapter.number}"
                url = "/br/manga/${chapter._id}"
                date_upload = dateFormat.tryParseDateTime(chapter.iso_date, ZoneId.of("America/Sao_Paulo"))
                scanlator = chapter.owners.joinToString(separator = ",", transform = { it.name })
            }
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfterLast("/")
        val result = client.get("$apiUrl/chapters/$id?selected_language=pt-br").parseAs<ChapterDetailsResponseDto>()
        return result.data.images.mapIndexed { index, image ->
            Page(index, imageUrl = image.default_url)
        }
    }

    private fun MangaDto.toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = poster?.default_url
        description = this@toSManga.description
        genre = genres.joinToString { it.name }
        url = "/br/manga/$_id"
    }

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
}
