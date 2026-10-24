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
    private val isAI: Int,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val title = this@Chapter.title?.takeIf { it.isNotEmpty() } ?: "Chapter $chapterNumber"
        name = if (isAI == 1) "$title (مترجم)" else title
        url = "/reader/$mangaSlug/$chapterNumber"
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class Tag(
    val name: String,
    val counter: Int = 0,
)

@Serializable
class TagsResponse(
    val data: List<Tag> = emptyList(),
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
    val text: String = "",
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
    val angle: Float = 0f,
    val color: String? = null,
    @SerialName("stroke_color") val strokeColor: String? = null,
    @SerialName("font_size_px") val fontSizePx: Float = 16f,
    @SerialName("line_height") val lineHeight: Float = 1.2f,
    @SerialName("stroke_width_px") val strokeWidthPx: Float = 0f,
    @SerialName("font_family") val fontFamily: String = "",
    @SerialName("text_align") val textAlign: String? = null,
)

@Serializable
class ChapterProps(
    val imageUrls: List<String> = emptyList(),
    val overlayBlob: String? = null,
    val overlayPageOffset: Int? = null,
    val textMode: String? = null,
    val chapterId: Long? = null,
    val unlockToken: String? = null,
)

@Serializable
class UnlockRequest(
    private val chapterId: Long,
    private val token: String,
    private val proof: String,
    private val esid: String,
    private val nonce: String? = null,
    private val pow: String? = null,
)

@Serializable
class UnlockResponse(
    val overlay: String? = null,
    val ks: String? = null,
    val key: String? = null,
    @SerialName("needs_challenge") val needsChallenge: Boolean = false,
    val nonce: String? = null,
    @SerialName("pow_bits") val powBits: Int? = null,
)
