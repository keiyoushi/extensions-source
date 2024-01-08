package eu.kanade.tachiyomi.extension.es.ikigaimangas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PayloadSeriesDto(
    val data: List<SeriesDto>,
    @SerialName("current_page")val currentPage: Int = 0,
    @SerialName("last_page") val lastPage: Int = 0,
)

@Serializable
data class SeriesDto(
    val id: Long,
    val name: String,
    val slug: String,
    val cover: String? = null,
    val type: String? = null,
    val summary: String? = null,
    val status: SeriesStatusDto? = null,
    val genres: List<FilterDto>? = null,
)

@Serializable
data class PayloadSeriesDetailsDto(
    val series: SeriesDto,
)

@Serializable
data class PayloadChaptersDto(
    var data: List<ChapterDto>,
)

@Serializable
data class ChapterDto(
    val id: Long,
    val name: String,
    @SerialName("published_at") val date: String,
)

@Serializable
data class PayloadPagesDto(
    val chapter: PageDto,
)

@Serializable
data class PageDto(
    val pages: List<String>,
)

@Serializable
data class SeriesStatusDto(
    val id: Long,
    val name: String,
)

@Serializable
data class PayloadFiltersDto(
    val data: GenresStatusesDto,
)

@Serializable
data class GenresStatusesDto(
    val genres: List<FilterDto>,
    val statuses: List<FilterDto>,
)

@Serializable
data class FilterDto(
    val id: Long,
    val name: String,
)
