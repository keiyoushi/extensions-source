package eu.kanade.tachiyomi.extension.pt.brscans

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class PaginatedManhwaDto(
    val next: String? = null,
    val results: List<ManhwaDto> = emptyList(),
)

@Serializable
class VariantsDto(
    val minimum: String? = null,
    val medium: String? = null,
    val original: String? = null,
    val translated: String? = null,
    val raw: String? = null,
    val upscaled: String? = null,
) {
    fun url(): String? {
        val path = translated?.takeIf { it.isNotEmpty() }
            ?: original?.takeIf { it.isNotEmpty() }
            ?: raw?.takeIf { it.isNotEmpty() }
            ?: upscaled?.takeIf { it.isNotEmpty() }
            ?: medium?.takeIf { it.isNotEmpty() }
            ?: minimum?.takeIf { it.isNotEmpty() }

        if (path.isNullOrEmpty()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path

        // If it's a relative path, resolve it relative to the S3/CloudFront domain
        return "https://d47sf0ah3vbtc.cloudfront.net/${path.removePrefix("/")}"
    }
}

@Serializable
class ManhwaDto(
    val id: Int,
    val title: String,
    val author: String? = null,
    val status: String? = null,
    val description: String? = null,
    val thumbnail: VariantsDto? = null,
    val genres: List<Int> = emptyList(),
    @SerialName("is_nsfw") val isNsfw: Boolean = false,
    val chapters: List<SimpleChapterDto> = emptyList(),
) {
    fun toSManga(genreMap: Map<Int, String> = emptyMap()) = SManga.create().apply {
        url = id.toString()
        title = this@ManhwaDto.title
        author = this@ManhwaDto.author?.takeIf { it.isNotBlank() }
        description = this@ManhwaDto.description?.takeIf { it.isNotBlank() }
        thumbnail_url = thumbnail?.url()

        status = when (this@ManhwaDto.status?.lowercase()) {
            "ongoing", "em lançamento", "ativo" -> SManga.ONGOING
            "completed", "completo", "finalizado" -> SManga.COMPLETED
            "hiatus", "pausa", "em pausa" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        genre = genres.mapNotNull { genreMap[it] }
            .joinToString { it }
            .takeIf { it.isNotBlank() }
    }
}

@Serializable
class SimpleChapterDto(
    val id: Int,
    val title: String,
    val slug: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toSChapter(dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = id.toString()
        name = title
        date_upload = (releaseDate ?: createdAt)?.let { dateStr ->
            // Date strings look like "2026-05-25T08:33:42Z" or similar ISO-8601 formats
            dateFormat.tryParse(dateStr.substringBefore("."))
        } ?: 0L
    }
}

@Serializable
class RecentChapterDto(
    val id: Int,
    val title: String,
    val slug: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val manhwa: ManhwaBriefDto? = null,
) {
    fun toSManga() = manhwa?.let {
        SManga.create().apply {
            url = it.id.toString()
            title = it.title
            thumbnail_url = it.thumbnail?.url()
        }
    }
}

@Serializable
class ManhwaBriefDto(
    val id: Int,
    val title: String,
    val slug: String? = null,
    val thumbnail: VariantsDto? = null,
)

@Serializable
class ChapterDetailDto(
    val id: Int,
    val title: String,
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val id: Int,
    val order: Int = 0,
    val images: VariantsDto? = null,
) {
    fun toPage(index: Int): Page? {
        val imgUrl = images?.url() ?: return null
        return Page(index, imageUrl = imgUrl)
    }
}

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
    val slug: String? = null,
)
