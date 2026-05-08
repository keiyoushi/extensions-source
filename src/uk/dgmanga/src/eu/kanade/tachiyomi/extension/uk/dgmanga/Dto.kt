package eu.kanade.tachiyomi.extension.uk.dgmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CatalogResponseDto(
    val titles: List<SearchResponseTitlesDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class SearchResponseTitlesDto(
    @SerialName("_id") val id: String,
    val title: String,
    val cover: String,
    val genres: Array<String> = arrayOf(),
    val type: String? = null,
)

@Serializable
class SMangaDto(
    @SerialName("_id") val id: String,
    val title: String,
    val cover: String,
    val alternativeTitles: Array<String>? = emptyArray<String>(),
    val description: String? = null,
    val type: String? = null,
    @SerialName("translation_status") val translationStatus: String? = null,
    val genres: Array<String?> = arrayOf(),
    val tags: Array<String?> = arrayOf(),
    val authorRef: List<SMangaStaffDto>,
    val illustratorRef: List<SMangaStaffDto>,
)

@Serializable
class SMangaStaffDto(
    val name: String? = null,
)

@Serializable
class ChapterResponseDto(
    @SerialName("_id") val id: String,
    val title: String,
    val chapterNumber: Float,
    val volumeNumber: Int,
    val chapterName: String? = null,
    val createdAt: String? = null,
    val teams: List<SMangaStaffDto>,
)
