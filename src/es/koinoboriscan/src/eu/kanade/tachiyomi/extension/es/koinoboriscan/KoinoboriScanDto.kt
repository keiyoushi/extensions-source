package eu.kanade.tachiyomi.extension.es.koinoboriscan

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SeriesDto(
    @SerialName("ID") private val id: Int,
    val title: String,
    private val description: String,
    private val thumbnail: String,
    private val status: String?,
    private val author: String?,
    private val tags: List<SeriesTagsDto>? = emptyList(),
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = this@SeriesDto.title
        thumbnail_url = cdnUrl + thumbnail
        url = id.toString()
    }

    fun toSMangaDetails(cdnUrl: String) = SManga.create().apply {
        title = this@SeriesDto.title.trim()
        author = this@SeriesDto.author?.trim()
        status = parseStatus(this@SeriesDto.status)
        thumbnail_url = cdnUrl + thumbnail
        genre = tags?.joinToString { it.name.trim() }
        description = this@SeriesDto.description.trim()
    }

    private fun parseStatus(status: String?) = when (status?.trim()) {
        "En emisión", "En curso" -> SManga.ONGOING
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
    val seasons: List<SeasonDto>,
)

@Serializable
class SeasonDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("ID") val id: Int,
    @SerialName("chapter_name") val name: String,
    @SerialName("chapter_title") val title: String,
    @SerialName("CreatedAt") val date: String,
)

@Serializable
class PagesPayloadDto(
    val chapter: ChapterImagesDto,
    val key: String,
)

@Serializable
class ChapterImagesDto(
    @SerialName("ID") val id: Int,
    val images: List<String>,
)
