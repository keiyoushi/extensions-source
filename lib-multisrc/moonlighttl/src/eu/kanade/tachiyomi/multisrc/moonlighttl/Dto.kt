package eu.kanade.tachiyomi.multisrc.moonlighttl

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class ResponseDto<T>(
    val response: T,
)

@Serializable
class TopSeriesDto(
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
) {
    fun toSManga(seriesPath: String): SManga = SManga.create().apply {
        title = name
        thumbnail_url = thumbnail
        url = "$seriesPath/$slug"
    }

    fun toSMangaDetails(intl: Intl): SManga = SManga.create().apply {
        title = name
        thumbnail_url = thumbnail
        description = synopsis
        if (!alternativeName.isNullOrBlank()) {
            if (!description.isNullOrBlank()) description += "\n\n"
            description += "${intl["alternative_names"]}: $alternativeName"
        }
        genre = genders.joinToString { it.gender.name }
        author = authors.joinToString { it.author.name }
        artist = artists.joinToString { it.artist.name }
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

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

@Serializable
class ChapterDto(
    @SerialName("num") private val number: Float,
    private val name: String? = null,
    private val slug: String,
    @SerialName("created_at") private val date: String,
) {
    fun toSChapter(seriesPath: String, seriesSlug: String, intl: Intl): SChapter = SChapter.create().apply {
        name = "${intl["chapter"]} ${number.toString().removeSuffix(".0")}"
        if (!this@ChapterDto.name.isNullOrBlank()) {
            name += " - ${this@ChapterDto.name}"
        }
        date_upload = dateFormat.tryParse(date)
        url = "$seriesPath/$seriesSlug/$slug"
    }
}
