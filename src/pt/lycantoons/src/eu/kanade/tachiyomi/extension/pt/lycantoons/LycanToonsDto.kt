package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.source.model.MangasPage
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
class PopularResponse(
    private val data: List<SeriesDto>,
    private val pagination: PaginationDto? = null,
) {
    fun toMangasPage() = MangasPage(data.map { it.toSManga() }, pagination?.hasNext == true)
}

@Serializable
class SeriesDto(
    private val title: String,
    private val slug: String,
    private val coverUrl: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val description: String? = null,
    private val genre: List<String>? = null,
    private val status: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@SeriesDto.title
        url = "/series/$slug"
        thumbnail_url = coverUrl
        author = this@SeriesDto.author?.takeIf { it.isNotBlank() && it != "-" }
        artist = this@SeriesDto.artist?.takeIf { it.isNotBlank() && it != "-" }
        genre = this@SeriesDto.genre?.takeIf { it.isNotEmpty() }
            ?.map { tagMapping[it] ?: it }
            ?.joinToString()
        description = this@SeriesDto.description
        status = parseStatus(this@SeriesDto.status)
        initialized = true
    }
}

@Serializable
class PaginationDto(
    val hasNext: Boolean? = null,
)

@Serializable
class SearchRequestBody(
    val limit: Int,
    val page: Int,
    val search: String,
    val seriesType: String,
    val status: String,
    val tags: List<String>,
)

@Serializable
class SearchResponse(
    private val series: List<SeriesDto>,
) {
    fun toMangasPage() = MangasPage(series.map { it.toSManga() }, false)
}

@Serializable
class ChapterResponse(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val numero: JsonElement,
    private val createdAt: String? = null,
    private val pageCount: Int? = null,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val numberString = numero.jsonPrimitive.content
        name = "Capítulo $numberString"
        url = "/series/$slug/$numberString" + (pageCount?.let { "?pages=$it" }.orEmpty())
        date_upload = dateFormat.tryParse(createdAt)
        chapter_number = numberString.toFloatOrNull() ?: -1f
    }
}

@Serializable
class FetchResult(
    val success: Boolean,
    val result: String,
    val contentType: String? = null,
)

private fun parseStatus(status: String?) = when (status?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}
