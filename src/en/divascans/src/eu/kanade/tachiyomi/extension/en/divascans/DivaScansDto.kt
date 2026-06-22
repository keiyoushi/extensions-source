package eu.kanade.tachiyomi.extension.en.divascans

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SeriesResponse(
    val data: List<MangaDto>? = null,
    val series: List<MangaDto>? = null,
    val items: List<MangaDto>? = null,
    val results: List<MangaDto>? = null,
    val mangas: List<MangaDto>? = null,
    val meta: MetaDto? = null,
    val totalPages: Int? = null,
) {
    val mangaList: List<MangaDto>?
        get() = data ?: series ?: items ?: results ?: mangas
}

@Serializable
data class MetaDto(
    val pagination: PaginationDto? = null,
)

@Serializable
data class PaginationDto(
    val page: Int? = null,
    val pageCount: Int? = null,
    val total: Int? = null,
)

@Serializable
data class MangaDto(
    val title: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val urlSlug: String? = null,
    val type: String? = null,
    val category: String? = null,
    val coverImage: String? = null,
    val thumbnail: String? = null,
    val description: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    val origin: String? = null,
    val genres: List<GenreWrapperDto>? = null,
    val tags: List<TagWrapperDto>? = null,
    val chapters: List<ChapterDto>? = null,
)

@Serializable
data class GenreWrapperDto(
    val genre: GenreDto? = null,
)

@Serializable
data class GenreDto(
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class TagWrapperDto(
    val tag: TagDto? = null,
    val slug: String? = null, // Sometimes tags are flat
)

@Serializable
data class TagDto(
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class ChapterDto(
    val number: JsonElement? = null,
    val title: String? = null,
    val isLocked: Boolean? = null,
    val coinPrice: Int? = null,
)

@Serializable
data class HydrationPayload(
    val d: String? = null,
)
