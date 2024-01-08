package eu.kanade.tachiyomi.extension.es.olympusscanlation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PayloadSeriesDto(val data: PayloadSeriesDataDto)

@Serializable
data class PayloadSeriesDataDto(
    val series: SeriesDto,
    val recommended_series: String,
)

@Serializable
data class SeriesDto(
    val current_page: Int,
    val data: List<MangaDto>,
    val last_page: Int,
)

@Serializable
data class PayloadMangaDto(val data: List<MangaDto>)

@Serializable
data class MangaDto(
    val id: Int,
    val name: String,
    val slug: String,
    val cover: String? = null,
    val type: String? = null,
    val summary: String? = null,
    val status: MangaStatusDto? = null,
    val genres: List<FilterDto>? = null,
)

@Serializable
data class NewChaptersDto(
    val data: List<LatestMangaDto>,
    val current_page: Int,
    val last_page: Int,
)

@Serializable
data class LatestMangaDto(
    val id: Int,
    val name: String,
    val slug: String,
    val cover: String? = null,
    val type: String? = null,
)

@Serializable
data class MangaDetailDto(
    var data: MangaDto,
)

@Serializable
data class PayloadChapterDto(var data: List<ChapterDto>, val meta: MetaDto)

@Serializable
data class ChapterDto(
    val id: Int,
    val name: String,
    @SerialName("published_at") val date: String,
)

@Serializable
data class MetaDto(val total: Int)

@Serializable
data class PayloadPagesDto(val chapter: PageDto)

@Serializable
data class PageDto(val pages: List<String>)

@Serializable
data class MangaStatusDto(
    val id: Int,
    val name: String,
)

@Serializable
data class GenresStatusesDto(
    val genres: List<FilterDto>,
    val statuses: List<FilterDto>,
)

@Serializable
data class FilterDto(
    val id: Int,
    val name: String,
)
