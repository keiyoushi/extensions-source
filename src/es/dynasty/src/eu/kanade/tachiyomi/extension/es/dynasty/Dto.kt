package eu.kanade.tachiyomi.extension.es.dynasty

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaPaginatedResponse(
    private val data: List<MangaDto> = emptyList(),
    private val totalPages: Int = 1,
) {
    fun getMangas() = data
    fun getTotalPages() = totalPages
}

@Serializable
class MangaDto(
    private val id: Int,
    val title: String,
    private val slug: String,
    private val description: String? = null,
    @SerialName("cover_image") private val coverImage: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    val type: String? = null,
    val views: Int? = 0,
    val rating: Float? = 0f,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        url = "$id|$slug"
        thumbnail_url = coverImage
        description = this@MangaDto.description
        author = this@MangaDto.author?.takeIf { it.isNotBlank() }
        artist = this@MangaDto.artist?.takeIf { it.isNotBlank() }
        genre = this@MangaDto.type?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterPaginatedResponse(
    private val chapters: List<ChapterDto> = emptyList(),
    private val totalPages: Int = 1,
) {
    fun getChapters() = chapters
    fun getTotalPages() = totalPages
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val number: Float? = null,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        name = buildString {
            if (number != null) append("Capítulo ${number.toString().removeSuffix(".0")}")
            if (!title.isNullOrBlank() && title != "null") {
                if (isNotEmpty()) append(" - ")
                append(title)
            }
        }
        if (name.isEmpty()) name = "Capítulo"
        url = id.toString()
        date_upload = createdAt?.let { Dynasty.Companion.parseDate(it) } ?: 0L
    }
}

@Serializable
class PageDto(
    @SerialName("image_url") private val imageUrl: String,
) {
    fun getUrl() = imageUrl
}
