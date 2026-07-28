package eu.kanade.tachiyomi.extension.ar.mangatek

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaDto(
    val manga: MangaData,
)

@Serializable
class MangaData(
    private val title: String,
    private val description: String,
    @SerialName("cover_image")
    private val cover: String,
    private val status: String,
    @SerialName("Tags")
    private val tags: List<Tag>,
    private val author: String,
    @SerialName("MangaChapters")
    val chapters: List<Chapter>,
) {

    fun toSManga(url: String) = SManga.create().apply {
        this.url = url
        title = this@MangaData.title
        description = this@MangaData.description
        genre = tags.map { it.name }.joinToString()
        thumbnail_url = cover
        status = when (this@MangaData.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        author = this@MangaData.author.takeUnless {
            it.isEmpty() || it.equals("unknown", true)
        }
    }
}

@Serializable
class Chapter(
    private val title: String?,
    @SerialName("chapter_number")
    private val chapterNumber: String,
    @SerialName("created_at")
    private val createdAt: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        name = this@Chapter.title?.takeIf {
            it.isNotBlank()
        } ?: "Chapter $chapterNumber"
        url = "/reader/$mangaSlug/$chapterNumber"
        date_upload = createdAt?.let {
            Instant.parseOrNull(it)?.toEpochMilliseconds()
        } ?: 0L
    }
}

@Serializable
class Tag(
    val name: String,
)

@Serializable
class PageDTO(
    val imageUrl: String,
    val bubbles: List<Bubble> = emptyList(),
) {
    fun hasSpeechBubbles() = bubbles.isNotEmpty()
}

@Serializable
class Bubble(
    val text: String = "",
    val left: Float = 0.0f,
    val top: Float = 0.0f,
    val width: Float = 0.0f,
    val height: Float = 0.0f,
    val angle: Float = 0.0f,
)
