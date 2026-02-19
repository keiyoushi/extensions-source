package eu.kanade.tachiyomi.extension.pt.mangaflix

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class BrowseResponseDto(
    val data: List<BrowseSectionDto> = emptyList(),
)

@Serializable
class BrowseSectionDto(
    val key: String,
    val items: JsonElement? = null,
)

@Serializable
class LatestResponseDto(
    val data: List<MangaDto> = emptyList(),
)

@Serializable
class SearchResponseDto(
    val data: List<SearchMangaDto> = emptyList(),
)

@Serializable
class GenreResponseDto(
    val data: List<MangaDto> = emptyList(),
    val metadata: MetadataDto? = null,
)

@Serializable
class GenreListResponseDto(
    val data: List<GenreItemDto> = emptyList(),
)

@Serializable
class GenreItemDto(
    val _id: String,
    val name: String,
)

@Serializable
class MetadataDto(
    val total: Int = 0,
    val items: Int = 0,
)

@Serializable
class MangaDto(
    val _id: String,
    val name: String,
    val description: String? = null,
    val poster: PosterDto? = null,
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class SearchMangaDto(
    val _id: String,
    val name: String,
    val description: String? = null,
    val poster: PosterDto? = null,
    val genres: List<String> = emptyList(),
)

@Serializable
class MangaDetailsResponseDto(
    val data: MangaDetailsDto,
)

@Serializable
class MangaDetailsDto(
    val _id: String,
    val name: String,
    val description: String? = null,
    val poster: PosterDto? = null,
    val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val _id: String,
    val name: String? = "",
    val number: String = "",
    val iso_date: String? = null,
    val owners: List<OwnerDto> = emptyList(),
)

@Serializable
class ChapterDetailsResponseDto(
    val data: ChapterDetailsDto,
)

@Serializable
class ChapterDetailsDto(
    val images: List<ImageDto> = emptyList(),
)

@Serializable
class ImageDto(
    val default_url: String,
)

@Serializable
class OwnerDto(
    val name: String,
)

@Serializable
class PosterDto(
    val default_url: String? = null,
)

@Serializable
class GenreDto(
    val name: String,
)
