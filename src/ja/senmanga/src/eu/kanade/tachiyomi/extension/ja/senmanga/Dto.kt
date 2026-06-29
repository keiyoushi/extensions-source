package eu.kanade.tachiyomi.extension.ja.senmanga

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class DirectoryResponse(
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val series: List<SeriesDto> = emptyList(),
)

@Serializable
class HomeResponse(
    val series: List<SeriesDto> = emptyList(),
)

@Serializable
class SeriesDto(
    private val title: String,
    val slug: String,
    private val cover: String? = null,
    private val status: String? = null,
    private val genre: String? = null,
    private val description: String? = null,
    val chapterList: List<ChapterDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        this.title = this@SeriesDto.title
        this.url = slug
        this.thumbnail_url = cover
        this.description = this@SeriesDto.description
        this.genre = this@SeriesDto.genre
        this.status = parseStatus(this@SeriesDto.status)
    }

    private fun parseStatus(statusString: String?): Int = when {
        statusString == null -> SManga.UNKNOWN
        statusString.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
        statusString.contains("complete", ignoreCase = true) -> SManga.COMPLETED
        statusString.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        statusString.contains("dropped", ignoreCase = true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class ChapterDto(
    val title: String,
    val url: String,
    val datetime: String? = null,
)

@Serializable
class ReadResponse(
    val pages: List<String> = emptyList(),
)
