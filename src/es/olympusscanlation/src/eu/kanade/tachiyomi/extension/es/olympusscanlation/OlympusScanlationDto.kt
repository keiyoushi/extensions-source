package eu.kanade.tachiyomi.extension.es.olympusscanlation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PayloadHomeDto(
    val data: HomeDto,
)

@Serializable
class HomeDto(
    @SerialName("popular_comics") val popularComics: String,
)

@Serializable
class PayloadSeriesDto(val data: PayloadSeriesDataDto)

@Serializable
class PayloadSeriesDataDto(
    val series: SeriesDto,
)

@Serializable
class SeriesDto(
    val current_page: Int,
    val data: List<MangaDto>,
    val last_page: Int,
)

@Serializable
class PayloadMangaDto(val data: List<MangaDto>)

@Serializable
class MangaDto(
    val name: String,
    val slug: String,
    val cover: String? = null,
    val type: String? = null,
    val summary: String? = null,
    val status: MangaStatusDto? = null,
    val genres: List<FilterDto>? = null,
)

@Serializable
class NewChaptersDto(
    val data: List<LatestMangaDto>,
    val current_page: Int,
    val last_page: Int,
)

@Serializable
class LatestMangaDto(
    val name: String,
    val slug: String,
    val cover: String? = null,
    val type: String? = null,
)

@Serializable
class MangaDetailDto(
    var data: MangaDto,
)

@Serializable
class PayloadChapterDto(var data: List<ChapterDto>, val meta: MetaDto)

@Serializable
class ChapterDto(
    val id: Int,
    val name: String,
    @SerialName("published_at") val date: String,
)

@Serializable
class MetaDto(val total: Int)

@Serializable
class PayloadPagesDto(val chapter: PageDto)

@Serializable
class PageDto(val pages: List<String>)

@Serializable
class MangaStatusDto(
    val id: Int,
)

@Serializable
class GenresStatusesDto(
    val genres: List<FilterDto>?,
    val statuses: List<FilterDto>?,
)

@Serializable
class FilterDto(
    val id: Int,
    val name: String,
)
