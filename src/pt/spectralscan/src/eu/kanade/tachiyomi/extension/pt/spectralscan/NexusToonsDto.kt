package eu.kanade.tachiyomi.extension.pt.spectralscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// ==================== API Response DTOs ====================

@Serializable
class MangaListResponse(
    val data: List<MangaListDto>? = null,
    val page: Int = 1,
    val pages: Int = 1,
)

@Serializable
class MangaListDto(
    val slug: String,
    val title: String,
    val coverImage: String? = null,
)

@Serializable
class MangaDetailsDto(
    val slug: String,
    val title: String,
    val description: String? = null,
    val coverImage: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val status: String,
    val categories: List<CategoryDto>? = null,
    val chapters: List<ChapterDto>? = null,
)

@Serializable
class CategoryDto(
    val name: String,
)

@Serializable
class ChapterDto(
    val id: Int,
    val number: String,
    val title: String? = null,
    val createdAt: String,
)

@Serializable
class ReadResponse(
    val pageToken: String,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val pageNumber: Int? = null,
    val imageUrl: String? = null,
)

@Serializable
class EncryptedResponse(
    val d: String,
    val k: Int = 0,
    val v: Int,
)

@Serializable
class GenreDto(
    val name: String,
    val id: Int,
    val type: String,
)

// ==================== Conversion Functions ====================

fun MangaListDto.toSManga() = SManga.create().apply {
    url = "/manga/$slug"
    title = this@toSManga.title
    thumbnail_url = coverImage
}

fun MangaDetailsDto.toSManga() = SManga.create().apply {
    url = "/manga/$slug"
    title = this@toSManga.title
    thumbnail_url = coverImage
    description = this@toSManga.description
    author = this@toSManga.author
    artist = this@toSManga.artist
    status = this@toSManga.status.parseStatus()
    genre = categories?.joinToString { it.name }
}

fun ChapterDto.toSChapter(mangaSlug: String) = SChapter.create().apply {
    url = "/read/$id/$mangaSlug"
    name = if (!title.isNullOrBlank()) {
        "$title $number"
    } else {
        "Capítulo ${number.removeSuffix(".0")}"
    }
    date_upload = Instant.tryParse(createdAt)
}

private fun String?.parseStatus() = when (this?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
