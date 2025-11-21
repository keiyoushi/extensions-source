package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class PopularResponse(
    val data: List<SeriesDto>,
    val pagination: PaginationDto? = null,
)

@Serializable
data class SeriesDto(
    val title: String,
    val slug: String,
    val coverUrl: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: String? = null,
    val seriesType: String? = null,
    val capitulos: List<ChapterDto>? = null,
)

@Serializable
data class PaginationDto(
    val page: Int? = null,
    val totalPages: Int? = null,
    val hasNext: Boolean? = null,
)

@Serializable
data class SearchRequestBody(
    val limit: Int,
    val page: Int,
    val search: String,
    val seriesType: String,
    val status: String,
    val tags: List<String>,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val numero: JsonElement,
    val createdAt: String? = null,
    val coverUrl: String? = null,
    val capaUrl: String? = null,
    val pageCount: Int? = null,
)

@Serializable
data class PageListDto(
    val numero: JsonElement,
    val pageCount: Int,
)

@Serializable
data class SearchResponse(
    val series: List<SeriesDto>,
)

fun SeriesDto.toSManga(): SManga = SManga.create().apply {
    title = this@toSManga.title
    url = "/series/$slug"
    thumbnail_url = coverUrl
    author = this@toSManga.author?.takeIf { it.isNotBlank() }
    artist = this@toSManga.artist?.takeIf { it.isNotBlank() }
    genre = this@toSManga.genre?.takeIf { it.isNotEmpty() }?.joinToString()
    description = this@toSManga.description
    status = parseStatus(this@toSManga.status)
}

fun ChapterDto.toSChapter(slug: String): SChapter = SChapter.create().apply {
    val numberString = numero.jsonPrimitive.content
    name = "Capítulo $numberString"
    val pagesQuery = pageCount?.let { "?pages=$it" }.orEmpty()
    url = "/series/$slug/$numberString$pagesQuery"
    date_upload = dateFormat.tryParse(createdAt)
    chapter_number = numberString.toFloatOrNull() ?: -1f
}

private fun parseStatus(status: String?): Int = when (status) {
    "ONGOING" -> SManga.ONGOING
    "COMPLETED" -> SManga.COMPLETED
    "HIATUS" -> SManga.ON_HIATUS
    "CANCELLED" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
