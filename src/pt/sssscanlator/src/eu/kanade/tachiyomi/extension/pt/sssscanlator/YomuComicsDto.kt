package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
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
    fun toSManga(): SManga = SManga.create().apply {
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
    fun toSManga(): SManga = SManga.create().apply {
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
    fun toSManga(): SManga = SManga.create().apply {
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
    val index: Double,
    val name: String,
    val date: String? = null,
    val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String, mangaId: Int): SChapter = SChapter.create().apply {
        val numberStr = if (index % 1 == 0.0) index.toInt().toString() else index.toString().replace(".", "-")
        name = this@ChapterDto.name
        chapter_number = index.toFloat()
        url = "/ler/$mangaSlug/capitulo-$numberStr?id=$mangaId"
        date_upload = DATE_FORMATTER.tryParse(createdAt)
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
        }
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

@Serializable
data class SeriesDto(
    val id: Int,
    val name: String,
    val sinopse: String? = null,
    val description: String? = null,
    val status: String? = null,
    val posterImage: String? = null,
    val coverImage: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val releasedAt: String? = null,
    val type: String? = null,
    val subType: String? = null,
    val views: Int? = null,
    val rating: Int? = null,
    val chaptersCount: Int? = null,
    val chapters: List<ChapterDto> = emptyList(),
    val favorites: Int? = null,
    val userInfo: String? = null,
) {
    fun toSManga(slug: String): SManga = SManga.create().apply {
        title = this@SeriesDto.name
        thumbnail_url = coverImage ?: posterImage
        description = sinopse ?: description
        genre = genres.joinToString(", ") { it.name }
        status = when (this@SeriesDto.status) {
            "ATIVO", "EM_DIA" -> SManga.ONGOING
            "CONCLUIDO" -> SManga.COMPLETED
            "HIATO" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        url = "/obra/$slug"
    }
}

@Serializable
data class GenreDto(
    val id: Int,
    val name: String,
)
