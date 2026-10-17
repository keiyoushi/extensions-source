package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

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
    val capitulos: List<ChapterDto>,
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
        date_upload = Instant.tryParse(createdAt)
        chapter_number = numberString.toFloatOrNull() ?: -1f
    }
}

@Serializable
class ChapterIdDto(
    val capituloId: Int,
)

@Serializable
class PageList(
    val pages: List<String>,
)

private fun parseStatus(status: String?) = when (status?.lowercase()) {
    "ongoing" -> SManga.ONGOING
    "completed" -> SManga.COMPLETED
    "hiatus" -> SManga.ON_HIATUS
    "cancelled" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
