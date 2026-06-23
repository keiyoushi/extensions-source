package eu.kanade.tachiyomi.extension.id.comicaso

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class HomeResponseDto(
    val data: List<MangaDto> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class TrendingResponseDto(
    val data: List<MangaDto> = emptyList(),
)

@Serializable
class MangaDto(
    private val slug: String,
    private val title: String,
    private val thumbnail: String? = null,
    private val source: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "${this@MangaDto.source ?: "all"}/$slug"
        title = this@MangaDto.title
        thumbnail_url = thumbnail
    }
}

@Serializable
class MangaDetailResponseDto(
    val data: MangaDetailDto,
)

@Serializable
class MangaDetailDto(
    val slug: String,
    private val title: String,
    private val thumbnail: String? = null,
    private val synopsis: String? = null,
    private val alternative: String? = null,
    private val status: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genres: List<String>? = emptyList(),
    val chapters: List<ChapterDto>? = emptyList(),
) {
    fun toSManga(source: String) = SManga.create().apply {
        url = "$source/$slug"
        title = this@MangaDetailDto.title
        thumbnail_url = thumbnail
        description = buildString {
            synopsis?.let { append(Jsoup.parseBodyFragment(it, "").text()) }
            alternative?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative: $it")
            }
        }
        author = this@MangaDetailDto.author
        artist = this@MangaDetailDto.artist
        genre = genres?.joinToString()
        status = when (this@MangaDetailDto.status?.lowercase()) {
            "on-going", "ongoing" -> SManga.ONGOING
            "end", "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val slug: String,
    private val title: String,
    private val date: Long? = null,
) {
    fun toSChapter(source: String, mangaSlug: String) = SChapter.create().apply {
        url = "$source/$mangaSlug/$slug"
        name = title
        date_upload = date?.let { it * 1000L } ?: 0L
    }
}

@Serializable
class ChapterResponseDto(
    val data: ChapterImagesDto,
)

@Serializable
class ChapterImagesDto(
    val images: List<String>? = null,
)
