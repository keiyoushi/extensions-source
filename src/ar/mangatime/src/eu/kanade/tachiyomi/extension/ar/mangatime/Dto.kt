package eu.kanade.tachiyomi.extension.ar.mangatime

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class TrpcEnvelope<T>(
    @SerialName("0") val first: TrpcData<T>,
) {
    constructor(input: T) : this(TrpcData(input))
}

@Serializable
class TrpcData<T>(
    val json: T,
)

@Serializable
class TrpcResponse<T>(
    val result: TrpcResult<T>,
)

@Serializable
class TrpcResult<T>(
    val data: TrpcData<T>,
)

// List Manga

@Serializable
class MangaListDto(
    val results: List<MangaSeries>,
    val hasMore: Boolean,
)

@Serializable
class MangaSeries(
    private val id: String,
    private val title: String,
    private val slug: String,
    private val coverUrl: String,
    private val type: String,
) {
    context(source: MangaTime)
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaSeries.title
        thumbnail_url = source.toImage(coverUrl)
        url = "/$type/$slug#$id"
        memo = buildJsonObject {
            put("id", id)
            put("slug", slug)
            put("type", type)
        }
    }
}

// Details

@Serializable
class SeriesDto(
    private val title: String,
    private val slug: String,
    private val coverUrl: String,
    private val type: String,
    private val genres: List<Genre>?,
    private val description: String?,
    private val status: String?,
) {
    context(source: MangaTime)
    fun toSManga(): SManga = SManga.create().apply {
        title = this@SeriesDto.title
        thumbnail_url = source.toImage(coverUrl)
        description = this@SeriesDto.description
        status = this@SeriesDto.status.toStatus()
        genre = ((this@SeriesDto.genres ?: emptyList()).map { it.name } + type)
            .filter { it.isNotBlank() }.joinToString().replace("\u060c", ",") // Arabic comma
    }

    private fun String?.toStatus() = when (this?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class Genre(
    val name: String,
)

// Chapters

@Serializable
class ChaptersDto(
    val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    private val number: JsonPrimitive,
    private val title: String,
    private val publishedAt: String? = null,
) {
    fun toSChapter(mangaUrl: String): SChapter = SChapter.create().apply {
        url = "$mangaUrl/chapter/$number"
        name = buildString {
            if (!title.contains(number.content)) {
                append("Chapter ")
                append(number)
                append(" - ")
            }
            append(title)
        }
        date_upload = publishedAt?.let {
            Instant.parseOrNull(it)?.toEpochMilliseconds()
        } ?: 0L
    }
}

// Pages

@Serializable
class PagesDto(
    val pages: List<String>,
    val isUnlocked: Boolean,
    val id: String,
    val seriesId: String,
)

// Request payloads

@Serializable
class SearchDto(
    val page: Int,
    val limit: Int,
    val sortBy: String,
    val sortOrder: String,
    val query: String? = null,
)

@Serializable
class SeriesSlug(
    val slug: String,
)

@Serializable
class ChaptersQuery(
    val seriesId: String,
    val limit: Int,
)

@Serializable
class PagesQuery(
    val seriesSlug: String,
    val chapterNumber: Int,
)

@Serializable
class ViewQuery(
    val seriesId: String,
    val chapterId: String,
)
