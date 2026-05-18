package eu.kanade.tachiyomi.extension.es.mantrazscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

private fun String.toAbsoluteUrl(baseUrl: String) = if (startsWith("/")) baseUrl + this else this

@Serializable
class BrowseResponse(
    private val data: BrowseData,
) {
    val series get() = data.series
    val hasNextPage get() = data.hasNextPage
}

@Serializable
class BrowseData(
    val series: List<SeriesDto>,
    private val page: Int,
    @SerialName("total_pages") private val totalPages: Int,
) {
    val hasNextPage get() = page < totalPages
}

@Serializable
class SeriesDto(
    val id: Int,
    val title: String,
    private val slug: String,
    @SerialName("cover_url") private val coverUrl: String,
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        this.title = this@SeriesDto.title
        thumbnail_url = coverUrl.toAbsoluteUrl(baseUrl)
        url = "$id#$slug"
    }
}

@Serializable
class DetailsResponse(
    private val data: DetailsData,
) {
    val series get() = data.series
}

@Serializable
class DetailsData(
    val series: SeriesDetailsDto,
)

@Serializable
class SeriesDetailsDto(
    private val id: Int,
    private val title: String,
    private val slug: String,
    private val description: String? = null,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val status: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genres: List<String> = emptyList(),
) {
    fun toSManga(baseUrl: String) = SManga.create().apply {
        url = "$id#$slug"
        this.title = this@SeriesDetailsDto.title
        thumbnail_url = coverUrl?.toAbsoluteUrl(baseUrl)
        this.description = this@SeriesDetailsDto.description
        author = this@SeriesDetailsDto.author?.takeIf { it.isNotBlank() }
        artist = this@SeriesDetailsDto.artist?.takeIf { it.isNotBlank() }
        genre = this@SeriesDetailsDto.genres.joinToString(", ")
        this.status = when (this@SeriesDetailsDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChaptersResponse(
    private val data: ChaptersData,
) {
    val chapters get() = data.chapters
}

@Serializable
class ChaptersData(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val id: Int,
    @SerialName("chapter_num") private val chapterNum: String,
    private val title: String? = null,
    private val slug: String,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "$id#$slug"
        name = buildString {
            append("Capítulo ")
            append(chapterNum.removeSuffix(".0"))
            if (!title.isNullOrBlank()) {
                append(" - ")
                append(title)
            }
        }
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class PagesResponse(
    private val data: PagesData,
) {
    val pages get() = data.chapter.pages
}

@Serializable
class PagesData(
    val chapter: ChapterDataDto,
)

@Serializable
class ChapterDataDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    @SerialName("image_url") private val imageUrl: String,
) {
    fun toPageImageUrl(baseUrl: String) = imageUrl.toAbsoluteUrl(baseUrl)
}
