package eu.kanade.tachiyomi.extension.es.mangaesp

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TopSeriesDto(
    val response: TopSeriesResponseDto,
)

@Serializable
data class LastUpdatesDto(
    val response: List<SeriesDto>,
)

@Serializable
data class ComicsDto(
    val response: List<SeriesDto>,
)

@Serializable
data class TopSeriesResponseDto(
    @SerialName("mensual") val topMonthly: List<List<PayloadSeriesDto>>,
    @SerialName("semanal") val topWeekly: List<List<PayloadSeriesDto>>,
    @SerialName("diario") val topDaily: List<List<PayloadSeriesDto>>,
)

@Serializable
data class PayloadSeriesDto(
    @SerialName("project") val data: SeriesDto,
)

@Serializable
data class SeriesDto(
    val name: String,
    val slug: String,
    @SerialName("sinopsis") val synopsis: String? = null,
    @SerialName("urlImg") val thumbnail: String? = null,
    val isVisible: Boolean,
    @SerialName("actualizacionCap") val lastChapterDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("state_id") val status: Int? = 0,
    val genders: List<SeriesGenderDto> = emptyList(),
    @SerialName("lastChapters") val chapters: List<SeriesChapterDto> = emptyList(),
    val trending: SeriesTrendingDto? = null,
    @SerialName("autors") val authors: List<SeriesAuthorDto> = emptyList(),
    val artists: List<SeriesArtistDto> = emptyList(),

) {
    fun toSimpleSManga(): SManga {
        return SManga.create().apply {
            title = name
            thumbnail_url = thumbnail
            url = "/ver/$slug"
        }
    }
}

@Serializable
data class SeriesTrendingDto(
    @SerialName("visitas") val views: Int? = 0,
)

@Serializable
data class SeriesGenderDto(
    val gender: SeriesDetailDataNameDto,
)

@Serializable
data class SeriesAuthorDto(
    @SerialName("autor") val author: SeriesDetailDataNameDto,
)

@Serializable
data class SeriesArtistDto(
    val artist: SeriesDetailDataNameDto,
)

@Serializable
data class SeriesDetailDataNameDto(
    val name: String,
)

@Serializable
data class SeriesChapterDto(
    @SerialName("num") val number: Float,
    val name: String? = null,
    val slug: String,
    @SerialName("created_at") val date: String,
)
