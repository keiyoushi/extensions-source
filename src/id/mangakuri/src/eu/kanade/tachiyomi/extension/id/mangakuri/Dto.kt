package eu.kanade.tachiyomi.extension.id.mangakuri

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat

@Serializable
class SearchResponseDto(
    val data: List<MangaDto>,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaDto(
    private val title: String,
    private val slug: String,
    @SerialName("poster_image_url") private val posterImageUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/comic/$slug"
        title = this@MangaDto.title
        thumbnail_url = posterImageUrl
    }
}

@Serializable
class SeriesDetailDto(
    private val title: String,
    val slug: String,
    private val synopsis: String? = null,
    @SerialName("poster_image_url") private val posterImageUrl: String? = null,
    @SerialName("comic_status") private val comicStatus: String? = null,
    @SerialName("author_name") private val authorName: String? = null,
    @SerialName("artist_name") private val artistName: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    val units: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/comic/$slug"
        title = this@SeriesDetailDto.title
        thumbnail_url = posterImageUrl
        author = authorName
        artist = artistName
        description = synopsis?.let { Jsoup.parseBodyFragment(it).text() }
        genre = genres.joinToString { it.name }
        status = when (comicStatus?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val slug: String,
    private val number: String,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(comicSlug: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/comic/$comicSlug/chapter/$slug"
        name = "Chapter ${number.removeSuffix(".00")}"
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = dateFormat.tryParse(createdAt)
    }
}

@Serializable
class ChapterDetailDto(
    val chapter: ChapterPagesDto,
)

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)
