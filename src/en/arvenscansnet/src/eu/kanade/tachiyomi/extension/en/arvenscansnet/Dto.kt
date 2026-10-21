package eu.kanade.tachiyomi.extension.en.arvenscansnet

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class DataDto<T>(val data: T)

@Serializable
class PagedDto<T>(
    val data: List<T>,
    private val meta: MetaDto,
) {
    val hasNextPage get() = meta.currentPage < meta.totalPages
    val totalPages get() = meta.totalPages
}

@Serializable
class MetaDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class WorkDto(
    private val id: String,
    private val slug: String,
    private val title: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val author: String? = null,
    private val status: String? = null,
    private val description: String? = null,
    private val tags: List<String> = emptyList(),
    @SerialName("alt_titles") private val altTitles: List<String> = emptyList(),
    private val genres: List<GenreDto>? = null,
) {
    fun toSManga(apiUrl: String, detailsGenres: List<GenreDto>? = null) = SManga.create().apply {
        url = id
        title = this@WorkDto.title
        thumbnail_url = coverUrl?.let { apiUrl + it }
        author = this@WorkDto.author?.takeIf { it.isNotBlank() }
        status = when (this@WorkDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = buildString {
            this@WorkDto.description?.let { append(it.replace("[", "\\[").replace("]", "\\]")) }
            val altNames = altTitles.filter { it.isNotBlank() }
            if (altNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles: ", altNames.joinToString(" · "))
            }
        }.ifEmpty { null }
        genre = ((detailsGenres ?: genres).orEmpty().map { it.name } + tags)
            .distinctBy { it.lowercase() }
            .joinToString()
            .ifEmpty { null }
        memo = buildJsonObject { put("slug", slug) }
    }
}

@Serializable
class GenreDto(
    val slug: String,
    @SerialName("name_en") val name: String,
    @SerialName("work_count") val workCount: Int? = null,
)

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("chapter_number") private val number: Double,
    private val title: String,
    val coins: Int,
    @SerialName("created_at") private val createdAt: String,
) {
    fun toSChapter() = SChapter.create().apply {
        url = id
        name = buildString {
            if (coins > 0) append("🔒 ")
            append("Chapter ", number.toString().removeSuffix(".0"))
            val chapterTitle = title.replace(WHITESPACE, " ").trim()
            if (chapterTitle.isNotEmpty()) append(" — ", chapterTitle)
        }
        chapter_number = number.toFloat()
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class ChapterPagesDto(
    val images: List<String>,
)

@Serializable
class WorkIdDto(
    @SerialName("work_id") val workId: String,
)

private val WHITESPACE = Regex("""\s+""")
