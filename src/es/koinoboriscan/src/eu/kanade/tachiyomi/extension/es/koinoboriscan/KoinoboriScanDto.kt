package eu.kanade.tachiyomi.extension.es.koinoboriscan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TopSeriesDto(
    val mensualRes: List<SeriesDto>,
    val weekRes: List<SeriesDto>,
    val dayRes: List<SeriesDto>,
)

@Serializable
class SeriesDto(
    @SerialName("series_slug") val slug: String,
    val title: String,
    private val description: String?,
    private val thumbnail: String?,
    private val status: String?,
    private val author: String?,
    private val tags: List<SeriesTagsDto>? = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@SeriesDto.title
        thumbnail_url = thumbnail
        url = slug
    }

    fun toSMangaDetails() = SManga.create().apply {
        title = this@SeriesDto.title.trim()
        author = this@SeriesDto.author?.trim()
        status = parseStatus(this@SeriesDto.status)
        thumbnail_url = thumbnail
        genre = tags?.joinToString { it.name.trim() }
        description = this@SeriesDto.description?.trim()
    }

    private fun parseStatus(status: String?) = when (status?.trim()) {
        "Ongoing" -> SManga.ONGOING
        "Completado" -> SManga.COMPLETED
        "Abandonado" -> SManga.CANCELLED
        "Pausado" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}

@Serializable
class SeriesTagsDto(
    val name: String,
)

@Serializable
class ChaptersPayloadDto(
    @SerialName("series_slug") val seriesSlug: String,
    @SerialName("Season") val seasons: List<SeasonDto>,
)

@Serializable
class SeasonDto(
    @SerialName("Chapter") val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_slug") val slug: String,
    @SerialName("chapter_name") val name: String,
    @SerialName("chapter_title") val title: String?,
    @SerialName("created_at") val date: String,
)
