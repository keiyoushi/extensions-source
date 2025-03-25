package eu.kanade.tachiyomi.extension.fr.phenixscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class LatestMangaResponse(
    val pagination: PaginationFront,
    val latest: List<LatestManga>,
)

@Serializable
class PaginationFront(
    val currentPage: Int,
    val totalPages: Int,
    val totalItems: Int,
)

@Serializable
class LatestManga(
    @SerialName("_id") val id: String,
    val title: String,
    val coverImage: String,
    val type: String,
    val slug: String,
    @SerialName("__v") val v: Int? = null,
)

@Serializable
class Chapter(
    @SerialName("_id") val id: String,
    val number: JsonPrimitive,
    val state: String,

    val price: Int? = 0,
    val freeAt: String? = null,

    val createdAt: String? = null,
    val publishAt: String? = null,

    val chapterUrl: String? = null,
)

@Serializable
class MangaDetailResponse(
    val manga: MangaDetail,
    val chapters: List<ChapterDetail>,

)

@Serializable
class MangaDetail(
    val title: String,
//    val genres: List<String>,  // This is wrong
    val coverImage: String? = null,
    val status: String? = "",
    val slug: String,
    val synopsis: String? = "",
//    val team: List<String>
)

@Serializable
class ChapterDetail(
    val number: JsonPrimitive,
    val createdAt: String?,
)

@Serializable
class TopResponse(
    val top: List<MangaDetail>,
)

@Serializable
class ChapterReadingDetail(
    val images: List<String>,
)

@Serializable
class ChapterReadingResponse(
    val chapter: ChapterReadingDetail,
)

@Serializable
class GenresDto(
    val data: List<GenreDto>,
)

@Serializable
class GenreDto(
    @SerialName("_id") val id: String,
    val name: String,
)

@Serializable
class PaginationFilter(
    val page: Int,
    val totalPages: Int,
)

@Serializable
class SearchResultDto(
    val mangas: List<LatestManga>,
    val pagination: PaginationFilter? = null,
)
