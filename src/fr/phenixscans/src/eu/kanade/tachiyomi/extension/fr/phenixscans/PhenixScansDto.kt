package eu.kanade.tachiyomi.extension.fr.phenixscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class LatestMangaResponse(
    val pagination: Pagination,
    val latest: List<LatestManga>,
)

@Serializable
data class Pagination(
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
    val chapters: List<Chapter>,
    val firstChapter: Chapter?,
    val synopsis: String = "",
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
