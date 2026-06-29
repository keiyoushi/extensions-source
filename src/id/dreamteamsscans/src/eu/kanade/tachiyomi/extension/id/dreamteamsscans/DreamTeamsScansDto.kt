package eu.kanade.tachiyomi.extension.id.dreamteamsscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class MangaListDto(
    val data: List<MangaDto>,
    val page: Int,
    val total_pages: Int,
)

@Serializable
data class MangaDto(
    val title: String,
    val slug: String,
    val poster_image_url: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        url = "/${this@MangaDto.slug}"
        thumbnail_url = poster_image_url ?: ""
    }
}

@Serializable
data class MangaDetailsDto(
    val title: String,
    val slug: String,
    val synopsis: String? = null,
    val poster_image_url: String? = null,
    val author_name: String? = null,
    val artist_name: String? = null,
    val comic_status: String? = null,
    val primary_genre: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val units: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDetailsDto.title
        url = "/${this@MangaDetailsDto.slug}"
        thumbnail_url = poster_image_url ?: ""
        description = synopsis
        author = author_name
        artist = artist_name
        status = when (comic_status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = (listOfNotNull(primary_genre) + genres.map { it.name }).distinct().joinToString()
    }
}

@Serializable
data class GenreDto(
    val name: String,
)

@Serializable
data class ChapterDto(
    val number: String,
    val slug: String,
    val title: String? = null,
    val created_at: String? = null,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "/comic/$mangaUrl/chapter/$slug"
        name = "Chapter ${number.removeSuffix(".00")}" + (if (!title.isNullOrBlank()) " - $title" else "")
        date_upload = dateFormat.tryParse(created_at)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}

@Serializable
data class PageListDto(
    val chapter: ChapterPageDto,
)

@Serializable
data class ChapterPageDto(
    val pages: List<PageDto>,
)

@Serializable
data class PageDto(
    val page_number: Int,
    val image_url: String,
)
