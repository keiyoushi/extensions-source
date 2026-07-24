package eu.kanade.tachiyomi.extension.id.soulscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
class SearchResultDto(
    @SerialName("data") val items: List<MangaDto> = emptyList(),
    val page: Int,
    val limit: Int,
    val total: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaDto(
    private val id: String,
    val title: String,
    private val slug: String,
    private val synopsis: String? = null,
    @SerialName("poster_image_url") private val posterImageUrl: String? = null,
    @SerialName("comic_status") private val comicStatus: String? = null,
    @SerialName("comic_subtype") private val comicSubtype: String? = null,
    @SerialName("author_name") private val authorName: String? = null,
    @SerialName("artist_name") private val artistName: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    val units: List<UnitDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "/comic/$slug"
        title = this@MangaDto.title
        thumbnail_url = posterImageUrl
        author = authorName
        artist = artistName

        // Status parsing
        status = when (comicStatus?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed", "complete" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        // Genre list construction
        val genreList = mutableListOf<String>()
        genres.forEach { genreList.add(it.name) }
        comicSubtype?.lowercase()?.replaceFirstChar { it.uppercase() }?.let {
            genreList.add(it)
        }
        genre = genreList.joinToString()
        description = synopsis
    }
}

@Serializable
class GenreDto(
    private val id: String,
    private val slug: String,
    val name: String,
)

@Serializable
class UnitDto(
    private val id: String,
    private val number: String,
    @SerialName("sort_number") val sortNumber: String,
    val slug: String,
    private val title: String,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/comic/$mangaSlug/chapter/$slug"

        val chapterNumberClean = number.removeSuffix(".00").removeSuffix(".0")
        name = if (title.startsWith("Chapter", ignoreCase = true)) {
            title
        } else {
            "Chapter $chapterNumberClean"
        }

        date_upload = runCatching {
            Instant.parse(createdAt).toEpochMilli()
        }.getOrDefault(0L)
    }
}

@Serializable
class ChapterPageResponseDto(
    val chapter: ChapterDto,
)

@Serializable
class ChapterDto(
    val id: String,
    val slug: String,
    val title: String,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val id: String,
    @SerialName("page_number") val pageNumber: Int,
    @SerialName("image_url") val imageUrl: String,
)
