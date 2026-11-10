package eu.kanade.tachiyomi.extension.tr.mikrokosmosfansub

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val zone = ZoneId.of("Europe/Istanbul")

@Serializable
class SeriesListDto(
    val data: List<SeriesDto>,
)

@Serializable
class SeriesDto(
    val slug: String,
    val title: String,
    val type: String,
    val status: String? = null,
    val rating: Float = 0f,
    val isDraft: Boolean = false,
    val updatedAt: String? = null,
    private val coverImage: String? = null,
    private val synopsis: String? = null,
    private val genres: List<String> = emptyList(),
    private val author: String? = null,
    private val artist: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = coverImage
        description = synopsis
        author = this@SeriesDto.author
        artist = this@SeriesDto.artist
        genre = (listOf(type) + genres).joinToString()
        status = when (this@SeriesDto.status) {
            "Devam Ediyor" -> SManga.ONGOING
            "Tamamlandı" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: Float,
    val publishedDate: String? = null,
    private val title: String,
    private val images: List<String> = emptyList(),
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        url = "$slug/$id"
        name = title
        chapter_number = number
        date_upload = dateFormat.tryParseDate(publishedDate, zone)
    }

    fun toPages() = images.mapIndexed { index, url -> Page(index, imageUrl = url) }
}
