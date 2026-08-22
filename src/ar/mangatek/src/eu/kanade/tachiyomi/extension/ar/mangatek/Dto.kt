package eu.kanade.tachiyomi.extension.ar.mangatek

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
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
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class Tag(
    val name: String,
)

@Serializable
class OverlayData(
    val pages: List<OverlayPage> = emptyList(),
)

@Serializable
class OverlayPage(
    @SerialName("page_number") val pageNumber: Int,
    val overlays: List<Bubble> = emptyList(),
)

@Serializable
class Bubble(
    val text: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val angle: Float = 0f,
    val color: String = "#000000",
    @SerialName("stroke_color") val strokeColor: String = "#ffffff",
    @SerialName("font_size_px") val fontSizePx: Float = 37.3f,
    @SerialName("line_height") val lineHeight: Float = 1.1f,
    @SerialName("stroke_width_px") val strokeWidthPx: Float = 3f,
)

@Serializable
class ChapterProps(
    val imageUrls: List<String>,
    val overlayBlob: String?,
)
