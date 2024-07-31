package eu.kanade.tachiyomi.extension.en.reaperscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetaDto(
    @SerialName("last_page")
    val lastPage: Int,
    @SerialName("current_page")
    val currentPage: Int,
)

@Serializable
data class SeriesQueryDto(
    val meta: MetaDto,
    val data: List<SeriesQueryItemDto>,
)

@Serializable
data class SeriesQueryItemDto(
    val id: Int,
    val title: String,
    val description: String,
    @SerialName("series_slug")
    val slug: String,
    val thumbnail: String,
    val status: String,
)

@Serializable
data class ChapterQueryDto(
    val meta: MetaDto,
    val data: List<ChapterQueryItemDto>,
)

@Serializable
data class ChapterQueryItemDto(
    @SerialName("chapter_name")
    val name: String,
    @SerialName("chapter_title")
    val title: String?,
    @SerialName("chapter_slug")
    val slug: String,
    @SerialName("created_at")
    val created: String,

    val series: ChapterSeriesDto,
)

@Serializable
data class ChapterSeriesDto(
    val id: Int,
    @SerialName("series_slug")
    val slug: String,
)
