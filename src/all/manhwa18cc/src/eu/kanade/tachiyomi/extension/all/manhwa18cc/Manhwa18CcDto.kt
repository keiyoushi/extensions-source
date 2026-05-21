package eu.kanade.tachiyomi.extension.all.manhwa18cc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Latest & Genre Browse ────────────────────────────────────────────────────

@Serializable
data class LatestResponseDto(
    val success: Boolean = false,
    val page: Int = 1,
    val totalPages: Int = 1,
    val manga: List<MangaItemDto> = emptyList(),
)

@Serializable
data class GenreResponseDto(
    val success: Boolean = false,
    val genre: String = "",
    val page: Int = 1,
    val totalPages: Int = 1,
    val manga: List<MangaItemDto> = emptyList(),
)

/** Lightweight manga entry used in list/browse/genre responses. */
@Serializable
data class MangaItemDto(
    val title: String = "",
    val slug: String = "",
    val thumbnail: String? = null,
)

// ─── Search ───────────────────────────────────────────────────────────────────

@Serializable
data class SearchResponseDto(
    val success: Boolean = false,
    val query: String = "",
    val count: Int = 0,
    val results: List<SearchResultDto> = emptyList(),
)

@Serializable
data class SearchResultDto(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
    val alternative: String = "",
    val thumbnail: String? = null,
    val rating: Float = 0f,
    @SerialName("isAdult") val isAdult: Boolean = false,
)

// ─── Manga Detail ─────────────────────────────────────────────────────────────

@Serializable
data class MangaDetailResponseDto(
    val success: Boolean = false,
    val slug: String = "",
    val title: String = "",
    val thumbnail: String? = null,
    val description: String = "",
    val alternativeNames: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val status: String = "",
    val year: String = "",
    val type: String = "",
    val rating: RatingDto = RatingDto(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
data class RatingDto(
    val value: Float = 0f,
    val count: Int = 0,
)

// ─── Chapter ──────────────────────────────────────────────────────────────────

/** Chapter stub used inside MangaDetailResponseDto. */
@Serializable
data class ChapterDto(
    val number: Float = 0f,
    val slug: String = "",
    val date: String? = null,
)

// ─── Chapter Reader ───────────────────────────────────────────────────────────

@Serializable
data class ChapterResponseDto(
    val success: Boolean = false,
    val mangaSlug: String = "",
    val chapterSlug: String = "",
    val title: String = "",
    val images: List<String> = emptyList(),
    val prevChapter: String? = null,
    val nextChapter: String? = null,
)

// ─── Genre List ───────────────────────────────────────────────────────────────

@Serializable
data class GenresResponseDto(
    val success: Boolean = false,
    val genres: List<String> = emptyList(),
)
