package eu.kanade.tachiyomi.extension.id.cosmicscansid

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.util.Locale
import kotlin.time.Instant

@Serializable
class MangaListResponse(
    val data: List<MangaDto> = emptyList(),
    val cursor: CursorDto? = null,
)

@Serializable
class CursorDto(
    val hasNext: Boolean = false,
    val nextCursor: String? = null,
)

@Serializable
class MangaDetailResponse(
    val data: MangaDetailDto = MangaDetailDto(),
)

@Serializable
class ReadingPageResponse(
    val data: ReadingPageDto = ReadingPageDto(),
)

@Serializable
class MangaDto(
    private val title: String? = null,
    private val slug: String? = null,
    private val cover: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val genres: List<String>? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title.orEmpty()
        url = "/series/${this@MangaDto.slug.orEmpty()}"
        thumbnail_url = this@MangaDto.cover
        genre = this@MangaDto.genres?.joinToString()
        status = when (this@MangaDto.status?.lowercase(Locale.ROOT)) {
            "ongoing" -> SManga.ONGOING
            "completed", "complete" -> SManga.COMPLETED
            "hiatus", "on hiatus", "on-hold", "on hold" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = this@MangaDto.type?.let { "Type: $it" }
        initialized = false
    }
}

@Serializable
class MangaDetailDto(
    private val title: String? = null,
    private val slug: String? = null,
    private val cover: String? = null,
    private val sinopsis: String? = null,
    private val type: String? = null,
    private val author: String? = null,
    private val genre: List<String>? = null,
    private val genres: List<String>? = null,
    private val status: String? = null,
    val chapters: List<ChapterDto>? = null,
) {
    fun toSMangaDetails(defaultSlug: String = ""): SManga = SManga.create().apply {
        val s = this@MangaDetailDto.slug?.takeIf { it.isNotBlank() } ?: defaultSlug
        title = this@MangaDetailDto.title.orEmpty()
        url = "/series/$s"
        thumbnail_url = this@MangaDetailDto.cover
        description = listOfNotNull(
            this@MangaDetailDto.sinopsis,
            this@MangaDetailDto.type?.let { "Type: $it" },
            this@MangaDetailDto.author?.let { "Author: $it" },
        ).joinToString("\n\n")
        genre = (this@MangaDetailDto.genre ?: this@MangaDetailDto.genres)?.joinToString()
        author = this@MangaDetailDto.author
        status = when (this@MangaDetailDto.status?.lowercase(Locale.ROOT)) {
            "ongoing" -> SManga.ONGOING
            "completed", "complete" -> SManga.COMPLETED
            "hiatus", "on hiatus", "on-hold", "on hold" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class ChapterDto(
    val slug: String? = null,
    private val chapterNum: String? = null,
    private val time: String? = null,
    @SerialName("redirect_link") val redirectLink: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = "Chapter ${this@ChapterDto.chapterNum.orEmpty()}".trim()
        url = "/chapter/${this@ChapterDto.slug.orEmpty()}"
        chapter_number = this@ChapterDto.chapterNum?.toFloatOrNull() ?: -1f
        date_upload = Instant.tryParse(this@ChapterDto.time)
    }
}

@Serializable
class ReadingPageDto(
    private val chapters: List<String>? = null,
    @SerialName("redirect_link") val redirectLink: String? = null,
) {
    fun toPageList(chapterUrl: String = ""): List<Page> = chapters.orEmpty()
        .mapNotNull { html ->
            Jsoup.parse(html).selectFirst("img")?.attr("src")
                ?.takeIf { it.isNotBlank() }
        }
        .mapIndexed { index, imageUrl -> Page(index, chapterUrl, imageUrl = imageUrl) }
}
