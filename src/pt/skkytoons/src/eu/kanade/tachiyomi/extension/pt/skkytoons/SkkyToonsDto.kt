package eu.kanade.tachiyomi.extension.pt.skkytoons

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

// ========================= API Response Wrapper =========================

@Serializable
class ApiResponse<T>(
    val success: Boolean,
    val data: T,
)

@Serializable
class ApiListResponse<T>(
    val success: Boolean,
    val data: List<T>,
    val pagination: PaginationDto? = null,
)

@Serializable
class PaginationDto(
    val total: Int,
    val limit: Int,
    val page: Int,
    val totalPages: Int,
    val hasMore: Boolean? = null,
    val hasNext: Boolean? = null,
) {
    fun hasNextPage(): Boolean = hasMore ?: hasNext ?: (page < totalPages)
}

// ========================= Manga DTOs =========================

@Serializable
class MangaDto(
    val id: String,
    val slug: String,
    val title: String,
    val alternativeTitles: String? = null,
    val description: String? = null,
    val coverImage: String? = null,
    val bannerImage: String? = null,
    val status: String? = null,
    val type: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val releaseYear: Int? = null,
    val views: Int? = null,
    val rating: Int? = null,
    val chapterCount: Int? = null,
    val isPublished: Boolean? = null,
    val isNsfw: Boolean? = null,
    val genres: List<GenreTagDto>? = null,
    val tags: List<GenreTagDto>? = null,
) {
    fun toSManga(apiUrl: String) = SManga.create().apply {
        url = "/manga/$slug"
        title = this@MangaDto.title
        thumbnail_url = coverImage?.let { "$apiUrl$it" }
        description = buildString {
            this@MangaDto.description?.let { append(it) }
            this@MangaDto.alternativeTitles?.takeIf { it.isNotBlank() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Títulos alternativos: $it")
            }
        }.takeIf { it.isNotBlank() }
        author = this@MangaDto.author
        artist = this@MangaDto.artist
        genre = buildList {
            this@MangaDto.genres?.map { it.name }?.let { addAll(it) }
            this@MangaDto.tags?.map { it.name }?.let { addAll(it) }
        }.distinct().joinToString().takeIf { it.isNotBlank() }
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GenreTagDto(
    val id: String,
    val name: String,
    val slug: String,
    val isNsfw: Boolean = false,
)

// ========================= Chapter DTOs =========================

@Serializable
class ChapterDto(
    val id: String,
    val mangaId: String,
    val chapterNumber: String,
    val title: String? = null,
    val slug: String? = null,
    val views: Int? = null,
    val isVipOnly: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/chapter/$id/$mangaSlug/$chapterNumber"
        name = buildString {
            val num = chapterNumber.toFloatOrNull()
            if (num != null) {
                append("Capítulo ${num.toString().removeSuffix(".0")}")
            }
            this@ChapterDto.title?.takeIf { it.isNotBlank() && !it.startsWith("Capítulo") }?.let {
                if (isNotEmpty()) append(" - ")
                append(it)
            }
        }.ifBlank { "Capítulo $chapterNumber" }
        chapter_number = chapterNumber.toFloatOrNull() ?: 0f
        date_upload = createdAt?.let { dateFormat.tryParse(it) } ?: 0L
    }
}

// ========================= Pages DTOs =========================

@Serializable
class ChapterPagesDto(
    val id: String,
    val mangaId: String,
    val chapterNumber: String,
    val title: String? = null,
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    val id: String,
    val chapterId: String,
    val pageNumber: Int,
    val imageUrl: String,
)

// ========================= Auth DTOs =========================

@Serializable
class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
class LoginResponseData(
    val user: UserDto? = null,
    val accessToken: String,
)

@Serializable
class UserDto(
    val id: String,
    val email: String,
    val username: String? = null,
    val name: String? = null,
)

// ========================= Browser Token DTOs =========================

@Serializable
class BrowserTokenResponse(
    val success: Boolean,
    val token: String,
)

@Serializable
class BrowserChallengeResponse(
    val success: Boolean,
    val challenge: ChallengeDto,
)

@Serializable
class ChallengeDto(
    val challengeId: String,
    val powChallenge: String,
    val powDifficulty: Int,
    val jsChallenge: JsChallengeDto,
    val expiresAt: Long,
    val signature: String,
)

@Serializable
class JsChallengeDto(
    val code: String,
    val expectedResultHash: String,
)

@Serializable
class TokenSolutionRequest(
    val challengeId: String,
    val powChallenge: String,
    val powNonce: String,
    val jsResult: String,
    val expiresAt: Long,
    val signature: String,
    val expectedResultHash: String,
)
