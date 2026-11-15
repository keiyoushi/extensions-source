package eu.kanade.tachiyomi.extension.id.koronaxscan

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class MangaListDto(
    val data: List<MangaDto>,
)

@Serializable
class MangaDetailDto(
    val data: MangaDto,
)

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    @SerialName("title_alt") val titleAlt: String? = null,
    private val description: String? = null,
    @SerialName("cover_image_url") private val coverImageUrl: String? = null,
    private val author: String? = null,
    private val artist: String? = null,
    private val status: String? = null,
    private val type: String? = null,
    private val rating: String? = null,
    private val project: Boolean = false,
    private val available: String? = null,
    private val genres: List<GenreDto> = emptyList(),
    val chapters: List<ChapterDto> = emptyList(),
) {
    val isListed get() = project && (available ?: "all") == "all"

    val ratingValue get() = rating?.toFloatOrNull() ?: 0f

    val latestChapterAt get() = chapters.maxOfOrNull { it.createdAt }

    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = coverImageUrl?.toImageUrl()
        author = this@MangaDto.author?.trim()?.takeIf { it.isNotEmpty() }
        artist = this@MangaDto.artist?.trim()?.takeIf { it.isNotEmpty() }
        description = buildString {
            this@MangaDto.description?.trim()?.takeIf { it.isNotEmpty() }?.let(::append)
            titleAlt?.trim()?.takeIf { it.isNotEmpty() }?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative title:\n- ").append(it)
            }
        }
        genre = (listOfNotNull(type) + genres.map { it.name }).joinToString()
        status = when (this@MangaDto.status?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_number") private val chapterNumber: Float,
    private val isPremium: Boolean = false,
    val createdAt: String,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        val number = chapterNumber.toString().removeSuffix(".0")
        url = "$slug/$number"
        name = if (isPremium) "🔒 Chapter $number" else "Chapter $number"
        chapter_number = chapterNumber
        date_upload = Instant.tryParse(createdAt)
    }
}

@Serializable
class PagesDto(
    val pages: List<PageDto>,
)

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)

// uploads stored on the API host come back as relative `/public/uploads/...` paths
fun String.toImageUrl() = if (startsWith("/")) KoronaxScan.API_URL + this else this
