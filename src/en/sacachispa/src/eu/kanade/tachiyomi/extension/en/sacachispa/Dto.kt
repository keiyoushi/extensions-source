package eu.kanade.tachiyomi.extension.en.sacachispa

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class SeriesResponseDto(
    val items: List<SeriesDto>,
    val page: Int,
    val totalPages: Int,
)

@Serializable
class SeriesDto(
    private val title: String,
    private val slug: String,
    private val description: String? = null,
    @SerialName("cover_image_url") private val coverImageUrl: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val genres: List<String> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        this.title = this@SeriesDto.title
        this.url = slug
        this.description = this@SeriesDto.description
        this.thumbnail_url = coverImageUrl
        this.author = this@SeriesDto.author
        this.artist = this@SeriesDto.artist
        this.genre = genres.joinToString()
        this.status = when (this@SeriesDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class RscChaptersDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class RscPageDto(
    val chapter: ChapterDto,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class ChapterDto(
    @SerialName("chapter_number") private val chapterNumber: Float,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    private val pages: List<String> = emptyList(),
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val numberStr = chapterNumber.toString().removeSuffix(".0")
        url = "/series/$slug/chapter/$numberStr"

        name = if (!title.isNullOrBlank()) {
            "Chapter $numberStr - $title"
        } else {
            "Chapter $numberStr"
        }

        chapter_number = chapterNumber
        date_upload = dateFormat.tryParse(createdAt)
    }

    fun toPageList(): List<Page> = pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
}
