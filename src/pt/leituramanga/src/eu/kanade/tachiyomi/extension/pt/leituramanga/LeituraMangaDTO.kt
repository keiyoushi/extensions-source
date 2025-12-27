package eu.kanade.tachiyomi.extension.pt.leituramanga

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class MangaResponseDto<T>(
    val data: T,
)

@Serializable
data class PopularDataDto(
    val topView: List<MangaDto>,
)

@Serializable
data class MangaListDto(
    val data: List<MangaDto>,
    val pagination: PaginationDto,
)

@Serializable
data class PaginationDto(
    val page: Int,
    val totalPage: Int,
)

@Serializable
data class MangaDto(
    val _id: String,
    val title: String,
    val slug: String,
    val author: String? = null,
    val description: String? = null,
    val status: String? = null,
    val genres: List<GenreDto>? = emptyList(),
    val alternativeTitles: List<String>? = emptyList(),
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = this@MangaDto.title
        url = "/manga/$slug"
        thumbnail_url = "$cdnUrl/$slug/cover-md.webp"
        author = this@MangaDto.author
        description = buildString {
            this@MangaDto.description?.let { append(it) }
            this@MangaDto.alternativeTitles?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Títulos alternativos: ${it.joinToString()}")
            }
        }
        status = when (this@MangaDto.status) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = this@MangaDto.genres?.joinToString { it.name }
    }
}

@Serializable
data class GenreDto(
    val name: String,
)

@Serializable
data class ChapterListDto(
    val data: List<ChapterDto>,
)

@Serializable
data class ChapterDto(
    val chapterNumber: Double,
    val title: String? = null,
    val releaseDate: String,
) {
    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        val numberStr = if (chapterNumber % 1 == 0.0) chapterNumber.toInt().toString() else chapterNumber.toString()
        name = title ?: "Capítulo $numberStr"
        url = "/manga/$mangaSlug/chapter/$numberStr"
        date_upload = dateFormat.tryParse(releaseDate) ?: 0L
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
    }
}
