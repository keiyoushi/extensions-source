package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

internal const val MANGA_API_PREFIX = "spa/manga"

fun chapterToString(chapterNumber: Float): String = chapterNumber.toString().removeSuffix(".0")

// Pattern: spa/manga/manga-id/chapter-number
fun SChapter.extractMangaId(): String = url // spa/manga/456789/123
    .removePrefix("$MANGA_API_PREFIX/") // 456789/123
    .substringBefore('/') // 456789

@Serializable
class MangaDetailDto(
    @SerialName("manga_id") val id: Int,
    @SerialName("manga_name") val name: String,
    @SerialName("manga_views") val views: Int,
    @SerialName("manga_cover_img") val coverImage: String,
    @SerialName("manga_others_name") val alternativeName: String? = null,
    @SerialName("manga_status") val status: Boolean = false,
    @SerialName("manga_description") val description: String? = null,
    @SerialName("manga_cover_img_full") val coverImageFull: String? = null,
    @SerialName("manga_date_published") val datePublished: String? = null,
)

@Serializable
class TagDto(
    @SerialName("tag_id") val id: Int,
    @SerialName("tag_name") val name: String,
)

@Serializable
class AuthorDto(
    @SerialName("author_id") val id: Int,
    @SerialName("author_name") val name: String,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_id") private val id: String,
    @SerialName("chapter_title") private val title: String,
    @SerialName("chapter_number") private val number: Float,
    @SerialName("chapter_views") private val views: Float,
    @SerialName("chapter_date_published") private val datePublished: String,
) {
    fun toSChapter(mangaId: Int) = SChapter.create().apply {
        val chapter = chapterToString(number)

        url = "$MANGA_API_PREFIX/$mangaId/$chapter"
        name = buildString {
            append("Chapter $chapter")

            if (title.isNotEmpty()) {
                append(": $title")
            }
        }
        chapter_number = number
        date_upload = Instant.parseOrNull(datePublished)?.toEpochMilliseconds() ?: 0L
    }
}

@Serializable
class MangaResponseDto(
    private val detail: MangaDetailDto,
    private val tags: List<TagDto>,
    private val authors: List<AuthorDto>,
    private val chapters: List<ChapterDto>,
) {
    private val dateFormatter by lazy {
        DateTimeFormatter
            .ofLocalizedDate(FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
    }

    fun toSManga() = SManga.create().apply {
        url = detail.id.toString()
        title = detail.name
        author = authors.joinToString { it.name }
        description = buildList {
            detail.datePublished?.takeIf { it.isNotBlank() }?.let {
                val instant = Instant.parseOrNull(it) ?: return@let
                val datePublished = instant.toJavaInstant().atZone(ZoneId.systemDefault())

                add("**Published:** ${dateFormatter.format(datePublished)}")
            }

            detail.views.takeIf { it > 0 }?.let {
                add("**Views:** $it")
            }

            detail.description?.takeIf { it.isNotBlank() }?.let {
                add("**Summary:**\n${detail.description}")
            }

            detail.alternativeName?.takeIf { it.isNotBlank() }?.let {
                add("**Alternative Titles:**\n$it")
            }
        }.joinToString("\n\n")
        genre = tags.joinToString { it.name }
        status = if (detail.status) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = detail.coverImageFull ?: detail.coverImage
    }

    fun toSChapterList() = chapters.map { it.toSChapter(detail.id) }
}
