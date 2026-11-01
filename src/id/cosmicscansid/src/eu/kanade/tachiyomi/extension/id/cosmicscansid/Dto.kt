package eu.kanade.tachiyomi.extension.id.cosmicscansid

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.time.Instant

@Serializable
class MangaListResponse(
    val data: List<MangaDto>,
    val cursor: CursorDto? = null,
)

@Serializable
class CursorDto(
    val hasNext: Boolean,
    val nextCursor: String? = null,
)

@Serializable
class MangaDetailResponse(
    val data: MangaDetailDto,
)

@Serializable
class ReadingPageResponse(
    val data: ReadingPageDto,
)

@Serializable
class MangaDto(
    private val title: String,
    private val slug: String,
    private val cover: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val genres: List<String>? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        url = "/series/${this@MangaDto.slug}"
        thumbnail_url = this@MangaDto.cover
        genre = this@MangaDto.genres?.joinToString()
        status = this@MangaDto.status.toStatus()
        description = this@MangaDto.type?.let { "Type: $it" }
    }
}

@Serializable
class MangaDetailDto(
    private val title: String,
    private val cover: String? = null,
    private val sinopsis: String? = null,
    private val type: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val genre: List<String>? = null,
    private val status: String? = null,
    val chapters: List<ChapterDto>? = null,
) {
    fun toSMangaDetails(slug: String): SManga = SManga.create().apply {
        title = this@MangaDetailDto.title
        url = "/series/$slug"
        thumbnail_url = this@MangaDetailDto.cover
        description = listOfNotNull(
            this@MangaDetailDto.sinopsis,
            this@MangaDetailDto.type?.let { "Type: $it" },
        ).joinToString("\n\n").takeIf { it.isNotEmpty() }
        genre = this@MangaDetailDto.genre?.joinToString()
        author = this@MangaDetailDto.author
        artist = this@MangaDetailDto.artist
        status = this@MangaDetailDto.status.toStatus()
    }
}

private fun String?.toStatus(): Int = when (this?.lowercase(Locale.ROOT)) {
    "ongoing" -> SManga.ONGOING
    "completed", "complete" -> SManga.COMPLETED
    "hiatus", "on hiatus", "on-hold", "on hold" -> SManga.ON_HIATUS
    "dropped" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}

@Serializable
class ChapterDto(
    private val slug: String,
    private val chapterNum: String,
    private val time: String? = null,
    @SerialName("redirect_link") val redirectLink: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = "Chapter ${this@ChapterDto.chapterNum}"
        url = "/chapter/${this@ChapterDto.slug}"
        date_upload = Instant.tryParse(this@ChapterDto.time)
    }
}

@Serializable
class ReadingPageDto(
    val pageCount: Int? = null,
    @SerialName("redirect_link") val redirectLink: String? = null,
)

@Serializable
class ImageConfigResponse(
    val data: ImageConfig,
)

@Serializable
class ImageConfig(
    val turnstileSiteKey: String,
)

@Serializable
class ImageSessionRequest(
    @SerialName("turnstile_token") private val turnstileToken: String,
    @SerialName("chapter_slug") private val chapterSlug: String,
)

@Serializable
class ImageSessionResponse(
    val data: ImageSession,
)

@Serializable
class ImageSession(
    val sessionId: String,
    val axisKeys: List<String>,
)
