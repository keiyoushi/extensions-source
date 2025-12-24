package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class HomeDto(
    val featuredManga: List<MangaDto> = emptyList(),
)

@Serializable
data class MangaDto(
    val title: String,
    val slug: String,
    val cover: String? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = cover
        url = "/obra/$slug"
    }
}

@Serializable
data class UpdatesDto(
    val updates: List<UpdateMangaDto> = emptyList(),
)

@Serializable
data class UpdateMangaDto(
    val title: String,
    val slug: String,
    val cover: String? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@UpdateMangaDto.title
        thumbnail_url = cover
        url = "/obra/$slug"
    }
}

@Serializable
data class SearchResponseDto(
    val results: List<SearchMangaDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
data class SearchMangaDto(
    val id: Int,
    val name: String,
    val slug: String? = null,
    val coverImage: String? = null,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@SearchMangaDto.name
        thumbnail_url = coverImage
        url = if (slug != null) "/obra/$slug" else "/series/$id"
    }
}

@Serializable
data class PaginationDto(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int,
)

@Serializable
data class DetailsResponseDto(
    val obraData: ObraDataDto,
)

@Serializable
data class ObraDataDto(
    val id: Int,
    val title: String,
    val slug: String,
    val synopsis: String? = null,
    val cover: String? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val author: String? = null,
    val artist: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@ObraDataDto.title
        thumbnail_url = cover
        description = synopsis
        author = this@ObraDataDto.author
        artist = this@ObraDataDto.artist
        genre = genres.joinToString(", ")
        status = when (this@ObraDataDto.status) {
            "ATIVO", "EM_DIA" -> SManga.ONGOING
            "CONCLUIDO" -> SManga.COMPLETED
            "HIATO" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        url = "/obra/$slug"
    }
}

@Serializable
data class ChapterDto(
    val id: Int,
    val number: Double,
    val title: String,
    val date: String? = null,
    val createdAt: String? = null,
) {
    fun toSChapter(mangaId: Int): SChapter = SChapter.create().apply {
        name = title
        chapter_number = number.toFloat()
        // Using API URL for page list fetching
        url = "/api/public/chapters/$mangaId/$number"
        date_upload = parseDate(date, createdAt)
    }

    private fun parseDate(date: String?, createdAt: String?): Long {
        if (createdAt != null && createdAt.startsWith("\$D")) {
            try {
                // Remove $D prefix and parse ISO date
                val isoDate = createdAt.removePrefix("\$D")
                return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(isoDate)?.time ?: 0L
            } catch (e: Exception) {
                // Ignore
            }
        }

        return 0L
    }
}

@Serializable
data class ChapterPagesDto(
    val pages: List<PageDto> = emptyList(),
)

@Serializable
data class PageDto(
    val url: String,
)

@Serializable
data class CsrfDto(val csrfToken: String)

@Serializable
data class SessionDto(val user: UserDto)

@Serializable
data class UserDto(val id: String)
