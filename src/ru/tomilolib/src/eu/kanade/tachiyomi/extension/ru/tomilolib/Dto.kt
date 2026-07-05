package eu.kanade.tachiyomi.extension.ru.tomilolib

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiResponse<T>(
    val data: T,
)

@Serializable
class TitlesData(
    val titles: List<TitleDto> = emptyList(),
    val pagination: Pagination = Pagination(),
)

@Serializable
class Pagination(
    val page: Int = 1,
    val pages: Int = 1,
)

@Serializable
class TitleDto(
    @SerialName("_id") val id: String,
    val name: String,
    val slug: String = "",
    val altNames: List<String> = emptyList(),
    val description: String = "",
    val genres: List<String> = emptyList(),
    val coverImage: String? = null,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val isAdult: Boolean = false,
)

@Serializable
class ChaptersData(
    val chapters: List<ChapterDto> = emptyList(),
    val pagination: Pagination = Pagination(),
)

@Serializable
class TitleRef(
    @SerialName("_id") val id: String = "",
)

@Serializable
class ChapterDto(
    @SerialName("_id") val id: String,
    val titleId: TitleRef = TitleRef(),
    val chapterNumber: Double = 0.0,
    val name: String? = null,
    val releaseDate: String? = null,
    val isPublished: Boolean = true,
    val isPaid: Boolean = false,
    val unlockPrice: Int = 0,
    val freeAt: String? = null,
)

@Serializable
class ChapterDetailDto(
    val pages: List<String> = emptyList(),
    val isPaid: Boolean = false,
)
