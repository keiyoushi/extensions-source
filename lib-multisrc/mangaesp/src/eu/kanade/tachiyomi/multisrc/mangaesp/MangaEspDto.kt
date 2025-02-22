package eu.kanade.tachiyomi.multisrc.mangaesp

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class TopSeriesDto(
    val response: TopSeriesResponseDto,
)

@Serializable
class LastUpdatesDto(
    val response: List<SeriesDto>,
)

@Serializable
class TopSeriesResponseDto(
    @SerialName("mensual") val topMonthly: List<List<PayloadSeriesDto>>,
    @SerialName("semanal") val topWeekly: List<List<PayloadSeriesDto>>,
    @SerialName("diario") val topDaily: List<List<PayloadSeriesDto>>,
)

@Serializable
class PayloadSeriesDto(
    @SerialName("project") val data: SeriesDto,
)

@Serializable
class SeriesDto(
    val name: String,
    val alternativeName: String? = null,
    val slug: String,
    @SerialName("sinopsis") private val synopsis: String? = null,
    @SerialName("urlImg") private val thumbnail: String? = null,
    @SerialName("actualizacionCap") val lastChapterDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("state_id") val status: Int? = 0,
    private val genders: List<GenderDto> = emptyList(),
    @SerialName("lastChapters") val chapters: List<ChapterDto> = emptyList(),
    val trending: TrendingDto? = null,
    @SerialName("autors") private val authors: List<AuthorDto> = emptyList(),
    private val artists: List<ArtistDto> = emptyList(),
    @Suppress("unused") // Used in some sources
    @SerialName("idioma")
    val language: String? = null,
) {
    fun toSManga(seriesPath: String): SManga {
        return SManga.create().apply {
            title = name
            thumbnail_url = thumbnail
            url = "$seriesPath/$slug"
        }
    }

    fun toSMangaDetails(): SManga {
        return SManga.create().apply {
            title = name
            thumbnail_url = thumbnail
            description = synopsis
            if (!alternativeName.isNullOrBlank()) {
                if (!description.isNullOrBlank()) description += "\n\n"
                description += "Nombres alternativos: $alternativeName"
            }
            genre = genders.joinToString { it.gender.name }
            author = authors.joinToString { it.author.name }
            artist = artists.joinToString { it.artist.name }
        }
    }
}

@Serializable
class TrendingDto(
    @SerialName("visitas") val views: Int? = 0,
)

@Serializable
class GenderDto(
    val gender: DetailDataNameDto,
)

@Serializable
class AuthorDto(
    @SerialName("autor") val author: DetailDataNameDto,
)

@Serializable
class ArtistDto(
    val artist: DetailDataNameDto,
)

@Serializable
class DetailDataNameDto(
    val name: String,
)

@Serializable
class ChapterDto(
    @SerialName("num") private val number: Float,
    private val name: String? = null,
    private val slug: String,
    @SerialName("created_at") private val date: String,
) {
    fun toSChapter(seriesPath: String, seriesSlug: String): SChapter {
        return SChapter.create().apply {
            name = "Capítulo ${number.toString().removeSuffix(".0")}"
            if (!this@ChapterDto.name.isNullOrBlank()) {
                name += " - ${this@ChapterDto.name}"
            }
            date_upload = try {
                DATE_FORMATTER.parse(date)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
            url = "$seriesPath/$seriesSlug/$slug"
        }
    }

    companion object {
        private val DATE_FORMATTER by lazy { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US) }
    }
}
