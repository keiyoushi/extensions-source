package eu.kanade.tachiyomi.extension.pt.sssscanlator

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class HomeDto(
    val featuredManga: List<MangaDto> = emptyList(),
)

@Serializable
class MangaDto(
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
class UpdatesDto(
    val updates: List<UpdateMangaDto> = emptyList(),
)

@Serializable
class UpdateMangaDto(
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
class SearchResponseDto(
    val results: List<SearchMangaDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
class SearchMangaDto(
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
class PaginationDto(
    val page: Int,
    val pages: Int,
)

@Serializable
class ChapterDto(
    val id: Int,
    val index: Double,
    val name: String,
    val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String, mangaId: Int): SChapter = SChapter.create().apply {
        val numberStr = index.toString().removeSuffix(".0")
        name = this@ChapterDto.name
        chapter_number = index.toFloat()
        url = "/ler/$mangaSlug/capitulo-$numberStr?id=$mangaId"
        date_upload = DATE_FORMATTER.tryParse(createdAt)
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }
}

@Serializable
class ChapterPagesDto(
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val url: String,
)

@Serializable
class CsrfDto(val csrfToken: String)

@Serializable
class SeriesDto(
    val id: Int,
    val name: String,
    val sinopse: String? = null,
    val description: String? = null,
    val status: String? = null,
    val posterImage: String? = null,
    val coverImage: String? = null,
    val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga(slug: String): SManga = SManga.create().apply {
        title = this@SeriesDto.name
        thumbnail_url = coverImage ?: posterImage
        description = sinopse ?: description
        genre = genres.joinToString { it.name }
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
class GenreDto(
    val name: String,
)
