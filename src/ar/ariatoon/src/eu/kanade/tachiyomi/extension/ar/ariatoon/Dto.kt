package eu.kanade.tachiyomi.extension.ar.ariatoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PaginatedResponseDto<T>(
    val data: List<T> = emptyList(),
)

@Serializable
class ItemResponseDto<T>(
    val data: T,
)

@Serializable
class MangaDto(
    private val id: String,
    private val title: String,
    private val coverPath: String,
    private val author: String? = null,
    private val summary: String? = null,
    private val status: String? = null,
    private val announce: String? = null,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        url = "/series/manga/$id"
        this.title = this@MangaDto.title
        thumbnail_url = "$cdnUrl/$coverPath"
        author = this@MangaDto.author
        this.status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        description = buildString {
            if (!summary.isNullOrBlank()) {
                append(summary)
            }
            if (!announce.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("إعلان:\n", announce)
            }
        }
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    // The API sends "mangaID" (all-caps), so @SerialName is needed to bridge the two.
    @SerialName("mangaID") private val mangaId: String,
    private val title: String? = null,
    private val number: Float? = null,
    internal val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/series/manga/$mangaId/episodes/$id"
        name = buildString {
            if (number != null) {
                append("الفصل ")
                append(number.toString().removeSuffix(".0"))
                if (!title.isNullOrBlank()) {
                    append(" - ")
                }
            }
            if (!title.isNullOrBlank()) {
                append(title)
            }
            if (isEmpty()) {
                append("الفصل")
            }
        }
        chapter_number = number ?: -1f
    }
}

@Serializable
class EpisodeDetailsDto(
    val images: List<String> = emptyList(),
)
