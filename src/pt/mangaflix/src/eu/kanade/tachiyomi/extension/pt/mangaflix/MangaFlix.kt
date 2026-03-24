package eu.kanade.tachiyomi.extension.pt.mangaflix

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangaFlix : HttpSource() {

    override val name = "MangaFlix"

    override val baseUrl = "https://mangaflix.net"

    private val apiUrl = "https://api.mangaflix.net/v1"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    private var genresList: List<Genre> = emptyList()
    private var fetchGenresAttempts = 0
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun launchIO(block: () -> Unit) = scope.launch { block() }

    private fun fetchGenres() {
        if (genresList.isNotEmpty() || fetchGenresAttempts >= 3) return
        fetchGenresAttempts++
        try {
            val response = client.newCall(GET("$apiUrl/genres?include_adult=true&selected_language=pt-br", headers)).execute()
            val result = response.parseAs<GenreListResponseDto>()
            genresList = result.data.map { Genre(it.name, it._id) }
        } catch (_: Exception) {
        }
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET("$apiUrl/browse", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<BrowseResponseDto>()
        val popularSection = result.data.firstOrNull { it.key == "most-read" }

        val mangas = popularSection?.items?.let { itemsElement ->
            itemsElement.parseAs<List<MangaDto>>().map { it.toSManga() }
        } ?: emptyList()

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET("$apiUrl/latest-releases?selected_language=pt-br", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<LatestResponseDto>()
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return GET("$apiUrl/search/mangas?query=$query&selected_language=pt-br", headers)
        }

        val genreFilter = filters.firstOrNull { it is GenreFilter } as? GenreFilter
        val genreId = genreFilter?.state?.firstOrNull { it.state }?.id

        return if (genreId != null) {
            val offset = (page - 1) * 20
            GET("$apiUrl/genres/$genreId/mangas/?offset=$offset&limit=20&include_adult=true", headers)
        } else {
            latestUpdatesRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url.toString()

        if (requestUrl.contains("/genres/")) {
            val result = response.parseAs<GenreResponseDto>()
            val mangas = result.data.map { it.toSManga() }

            val currentOffset = response.request.url.queryParameter("offset")?.toIntOrNull() ?: 0
            val totalItems = result.metadata?.total ?: 0
            val pageItems = result.data.size

            return MangasPage(mangas, currentOffset + pageItems < totalItems)
        }

        if (requestUrl.contains("latest-releases")) {
            return latestUpdatesParse(response)
        }

        val result = response.parseAs<SearchResponseDto>()
        val mangas = result.data.map { item ->
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

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        return if (genresList.isNotEmpty()) {
            FilterList(GenreFilter(genresList))
        } else {
            FilterList(Filter.Header("Aperte 'Redefinir' para tentar carregar os gêneros"))
        }
    }

    // =========================== Manga Details ============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/mangas/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailsResponseDto>()
        val details = result.data
        return SManga.create().apply {
            title = details.name
            thumbnail_url = details.poster?.default_url
            description = details.description
            genre = details.genres.joinToString { it.name }
            author = details.chapters.firstOrNull()?.owners?.firstOrNull()?.name
            status = SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailsResponseDto>()
        return result.data.chapters.map { chapter ->
            SChapter.create().apply {
                chapter_number = chapter.number.toFloatOrNull() ?: 0F
                name = chapter.name?.ifBlank { null } ?: "Capítulo ${chapter.number}"
                url = "/br/manga/${chapter._id}"
                date_upload = chapter.iso_date?.let { dateFormat.tryParse(it) } ?: 0L
                scanlator = chapter.owners.joinToString(separator = ",", transform = { it.name })
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/chapters/$id?selected_language=pt-br", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterDetailsResponseDto>()
        return result.data.images.mapIndexed { index, image ->
            Page(index, imageUrl = image.default_url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun MangaDto.toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = poster?.default_url
        description = this@toSManga.description
        genre = genres.joinToString { it.name }
        url = "/br/manga/$_id"
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("America/Sao_Paulo")
        }
    }
}
