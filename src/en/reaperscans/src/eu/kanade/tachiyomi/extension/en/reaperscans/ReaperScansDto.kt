package eu.kanade.tachiyomi.extension.en.reaperscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MetaDto(
    @SerialName("last_page")
    val lastPage: Int,
    @SerialName("current_page")
    val currentPage: Int? = 1, // is null if no results
)

@Serializable
class SeriesQueryDto(
    val meta: MetaDto,
    val data: List<SeriesQueryItemDto>,
)

@Serializable
class SeriesQueryItemDto(
    val id: Int,
    val title: String,
    val description: String,
    @SerialName("series_slug")
    val slug: String,
    val thumbnail: String,
    val status: String,
)

@Serializable
class ChapterQueryDto(
    val data: List<ChapterQueryItemDto>,
)

@Serializable
class ChapterQueryItemDto(
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
class ChapterSeriesDto(
    @SerialName("series_slug")
    val slug: String,
)

@Serializable
class SeriesDto(
    val id: Int,
    val title: String,
    val description: String,
    @SerialName("series_slug")
    val slug: String,
    val thumbnail: String,
    val status: String,
    val author: String,
    val tags: List<TagDto>,
)

@Serializable
class TagDto(
    val name: String,
)

@Serializable
class ChapterDataOuterDto(
    val chapter: ChapterDataDto,
)

@Serializable
class ChapterDataDto(
    @SerialName("chapter_data")
    val data: ChapterDataInnerDto,
)

@Serializable
class ChapterDataInnerDto(
    val images: List<String>,
)
