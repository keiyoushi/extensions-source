package eu.kanade.tachiyomi.extension.es.catmanhwas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.text.SimpleDateFormat
import java.util.TimeZone

@Serializable
class SvelteDataDto(
    val nodes: List<SvelteNodeDto>,
) {
    fun getDataNode(): JsonArray = nodes
        .first { it.type == "data" }
        .data!!
}

@Serializable
class SvelteNodeDto(
    val type: String,
    val data: JsonArray? = null,
)

@Serializable
class SvelteResultDto(
    private val result: String,
) {
    fun getResult() = Json.parseToJsonElement(result)
}

@Serializable
class BrowseDto(
    val series: List<SeriesDto>,
    private val lastPage: Int,
    private val page: Int,
) {
    fun hasNextPage() = page < lastPage
}

@Serializable
class SeriesDto(
    private val name: String,
    private val slug: String,
    @SerialName("cover_url") private val coverUrl: String?,
    private val genres: List<GenreDto>?,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = coverUrl
        url = slug
        genre = genres?.joinToString { it.name }
    }
}

@Serializable
class DetailsDto(
    private val status: String? = null,
    private val type: String? = null,
    private val genres: List<GenreDto>? = emptyList(),
) {
    fun getStatus() = when (status) {
        "on-going" -> SManga.ONGOING
        "end" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    fun getGenres() = buildList {
        type?.let { type -> add(type.replaceFirstChar { it.uppercase() }) }
        genres?.forEach { add(it.name) }
    }.joinToString()
}

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDataDto(
    val data: List<ChapterDto>,
    val pagination: PaginationDto,
)

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'").apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val number: Float,
    private val name: String? = null,
    @SerialName("published_at") private val publishedAt: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$id"
        name = buildString {
            append("Capítulo ${number.toString().removeSuffix(".0")}")
            this@ChapterDto.name?.let { append(": $it") }
        }
        date_upload = dateFormat.parse(publishedAt)?.time ?: 0L
    }
}

@Serializable
class PaginationDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") private val lastPage: Int,
) {
    fun hasNextPage() = currentPage < lastPage
}
