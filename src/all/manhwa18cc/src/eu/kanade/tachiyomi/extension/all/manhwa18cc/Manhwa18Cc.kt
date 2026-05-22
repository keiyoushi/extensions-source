package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

abstract class Manhwa18Cc(override val lang: String) : HttpSource() {

    override val name = "Manhwa18.cc"
    override val baseUrl = "https://manhwa18.cc"
    override val supportsLatest = true

    private val apiUrl = "https://manhwa18api.vercel.app/api"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    /**
     * Language predicate — override in EN/KO subclasses.
     * Titles ending in "Raw" are treated as Korean; everything else as English.
     * Default (ALL): accept everything.
     */
    open fun isMangaForLang(title: String): Boolean = true

    // --- Popular ---

    override fun popularMangaRequest(page: Int): Request =
        GET("$apiUrl/latest?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<LatestResponseDto>()
        val mangas = dto.manga
            .filter { isMangaForLang(it.title) }
            .map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = dto.page < dto.totalPages)
    }

    // --- Latest ---

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiUrl/latest?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // --- Search ---

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Keyword search
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
            return GET(url, headers)
        }

        // ... Genre filter
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selected
        return if (!genre.isNullOrBlank() && genre != GENRE_ALL) {
            val genreSlug = genre.lowercase().replace(" ", "-")
            GET("$apiUrl/genre/$genreSlug?page=$page", headers)
        } else {
            popularMangaRequest(page)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val reqUrl = response.request.url.toString()
        val body = response.body.string()

        return when {
            "/search" in reqUrl -> {
                val dto = json.decodeFromString<SearchResponseDto>(body)
                val mangas = dto.results
                    .filter { isMangaForLang(it.name) }
                    .map { it.toSManga() }
                MangasPage(mangas, hasNextPage = false) // search results are not paginated
            }
            "/genre/" in reqUrl -> {
                val dto = json.decodeFromString<GenreResponseDto>(body)
                val mangas = dto.manga
                    .filter { isMangaForLang(it.title) }
                    .map { it.toSManga() }
                MangasPage(mangas, hasNextPage = dto.page < dto.totalPages)
            }
            else -> {
                // Fallback: treat as latest
                val dto = json.decodeFromString<LatestResponseDto>(body)
                val mangas = dto.manga
                    .filter { isMangaForLang(it.title) }
                    .map { it.toSManga() }
                MangasPage(mangas, hasNextPage = dto.page < dto.totalPages)
            }
        }
    }

    // --- Manga Details ---
    // manga.url format: /webtoon/{slug}
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.trimStart('/').removePrefix("webtoon/").substringBefore("/")
        return GET("$apiUrl/manga/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDetailResponseDto>()
        return SManga.create().apply {
            url = "/webtoon/${dto.slug}"
            title = dto.title
            thumbnail_url = dto.thumbnail
            description = dto.description
            author = dto.authors.joinToString(", ")
            artist = dto.artists.joinToString(", ")
            genre = dto.genres.joinToString(", ")
            status = dto.status.toMangaStatus()
            initialized = true
        }
    }

    // --- Chapter List ---
    // Reuse the manga detail endpoint — it already returns the full chapter list.
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<MangaDetailResponseDto>()
        return dto.chapters.map { chapter ->
            SChapter.create().apply {
                url = "/webtoon/${dto.slug}/${chapter.slug}"
                name = "Chapter ${chapter.number.toDisplay()}"
                chapter_number = chapter.number
                date_upload = chapter.date.parseDate()
            }
        }
    }

    // --- Page List ---

    // chapter.url format: /webtoon/{mangaSlug}/{chapterSlug}
    override fun pageListRequest(chapter: SChapter): Request {
        val segments = chapter.url.trimStart('/').split("/")
        // index 0 = "webtoon", 1 = mangaSlug, 2 = chapterSlug
        val mangaSlug = segments.getOrElse(1) { "" }
        val chapterSlug = segments.getOrElse(2) { "" }
        return GET("$apiUrl/chapter/$mangaSlug/$chapterSlug", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterResponseDto>()
        return dto.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl, imageUrl)
        }
    }

    // imageUrlParse is unused because Page already carries imageUrl directly.
    override fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("imageUrlParse is not used by this source")

    // --- Filters ---

    private var cachedGenres: List<String> = emptyList()

    override fun getFilterList(): FilterList {
        val header = if (cachedGenres.isEmpty()) {
            Filter.Header("Press Reset to load genres, then reopen the filter")
        } else {
            Filter.Header("Filter by genre (leave query empty)")
        }
        val genres = listOf(GENRE_ALL) + cachedGenres
        return FilterList(header, GenreFilter(genres))
    }

    /**
     * Fetch genre list from the API and cache it for getFilterList().
     * Called automatically by the app when the user taps "Reset".
     */
    override fun fetchGenres() = client.newCall(GET("$apiUrl/genres", headers))
        .execute()
        .let { response ->
            runCatching {
                val dto = response.parseAs<GenresResponseDto>()
                if (dto.success) {
                    cachedGenres = dto.genres.map { it.replaceFirstChar(Char::titlecase) }
                }
            }
        }

    // --- Private Helpers ---

    /** Deserialize the response body into [T] using the shared [json] instance. */
    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    /** Map API status strings → Tachiyomi SManga status constants. */
    private fun String.toMangaStatus(): Int = when (lowercase().trim()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "dropped", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    /** Parse ISO date strings (yyyy-MM-dd). Returns 0 on failure. */
    private fun String?.parseDate(): Long {
        if (isNullOrBlank()) return 0L
        return runCatching { DATE_FORMAT.parse(this)?.time ?: 0L }.getOrDefault(0L)
    }

    /**
     * Display a Float chapter number without a trailing ".0" for whole numbers.
     * e.g. 92.0 → "92", 92.5 → "92.5"
     */
    private fun Float.toDisplay(): String =
        if (this == toLong().toFloat()) toLong().toString() else toString()

    // --- DTO - Source Model Conversions ---

    private fun MangaItemDto.toSManga() = SManga.create().apply {
        url = "/webtoon/$slug"
        title = this@toSManga.title
        thumbnail_url = thumbnail
    }

    private fun SearchResultDto.toSManga() = SManga.create().apply {
        url = "/webtoon/$slug"
        title = name
        thumbnail_url = thumbnail
    }

    // --- Constants ---

    companion object {
        private const val GENRE_ALL = "All"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}

// --- Filter Definitions ---

class GenreFilter(genres: List<String>) :
    Filter.Select<String>("Genre", genres.toTypedArray()) {
    val selected: String get() = values[state]
}
