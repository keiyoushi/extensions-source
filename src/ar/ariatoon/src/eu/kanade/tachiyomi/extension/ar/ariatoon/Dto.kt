package eu.kanade.tachiyomi.extension.ar.ariatoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat by lazy {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

@Serializable
class PaginatedResponseDto<T>(
    val data: List<T>? = null,
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
        url = id
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
        initialized = true
    }
}

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("mangaID") private val mangaId: String,
    private val title: String? = null,
    private val number: Float? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "$mangaId/episodes/$id"
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
        date_upload = dateFormat.tryParse(createdAt?.substringBefore("."))
    }
}

@Serializable
class EpisodeDetailsDto(
    val images: List<String> = emptyList(),
)
