package eu.kanade.tachiyomi.extension.id.komiknesia

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PayloadDto<T>(
    val data: T,
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    val page: Int,
    @SerialName("total_pages")
    val totalPages: Int,
)

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
    @SerialName("alternative_name")
    private val alternativeName: String? = null,
    private val author: String? = null,
    private val sinopsis: String? = null,
    private val cover: String? = null,
    private val status: String? = null,
    private val genres: List<GenreDto>? = emptyList(),
    val chapters: List<ChapterDto>? = emptyList(),
) {
    companion object {
        private val PARAGRAPH_REGEX = Regex("</?p\\s*/?>")
    }

    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = cover
        author = this@MangaDto.author
        description = buildString {
            sinopsis?.let {
                append(it.replace(PARAGRAPH_REGEX, "").trim())
            }
            if (!alternativeName.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative Names:")
                alternativeName.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                    append("\n$it")
                }
            }
        }
        genre = genres?.joinToString { it.name }
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    private val number: String,
    private val title: String,
    private val slug: String,
    @SerialName("created_at")
    private val createdAt: DateDto? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = slug
        name = title
        chapter_number = number.toFloatOrNull() ?: -1f
        date_upload = (createdAt?.time ?: 0L) * 1000L
    }
}

@Serializable
class DateDto(
    val time: Long,
)

@Serializable
class PageListDto(
    val images: List<String>,
)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)
