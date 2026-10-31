package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class IkiruResponseDto<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
)

@Serializable
class IkiruSearchDataDto(
    val mangas: List<IkiruMangaItemDto> = emptyList(),
    val total: Int,
)

@Serializable
class IkiruProjectDataDto(
    val project: List<IkiruMangaItemDto> = emptyList(),
    val total: Int,
)

@Serializable
class IkiruMangaItemDto(
    private val title: String,
    private val slug: String,
    private val featuredImage: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        this.title = this@IkiruMangaItemDto.title
        this.url = slug
        thumbnail_url = featuredImage
    }
}

private fun String?.toPlainText(): String? {
    this ?: return null
    val document = Jsoup.parseBodyFragment(this)
    return document.select("p")
        .map { it.wholeText() }
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
        .ifEmpty { document.body().wholeText() }
}

@Serializable
class IkiruMangaDetailDto(
    private val title: String,
    private val slug: String,
    private val description: String? = null,
    private val status: String? = null,
    private val featuredImage: String? = null,
    private val metadata: IkiruMetadataDto? = null,
    val chapters: IkiruChapterListDto = IkiruChapterListDto(),
) {
    fun toSManga(): SManga {
        // the site repeats the main title inside alternateTitles
        val altTitles = metadata?.alternateTitles.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(title, ignoreCase = true) }
            .distinct()
        val plainDescription = description.toPlainText()

        return SManga.create().apply {
            this.title = this@IkiruMangaDetailDto.title
            this.url = slug
            thumbnail_url = featuredImage
            this.description = buildString {
                if (!plainDescription.isNullOrBlank()) append(plainDescription)
                if (altTitles.isNotEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative Names:\n")
                    append(altTitles.joinToString("\n") { "- $it" })
                }
            }.trim()
            this.status = when (this@IkiruMangaDetailDto.status?.uppercase()) {
                "ONGOING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "HIATUS" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            author = metadata?.author?.mapNotNull { it.name }?.joinToString(", ")
            artist = metadata?.artist?.mapNotNull { it.name }?.joinToString(", ")
            genre = metadata?.genre?.mapNotNull { it.name }?.joinToString(", ")
        }
    }
}

// the previous multisrc persisted manga urls as JSON blobs holding the slug
@Serializable
class IkiruLegacyMangaUrlDto(
    val slug: String = "",
)

@Serializable
class IkiruMetadataDto(
    val alternateTitles: List<String> = emptyList(),
    val genre: List<IkiruNamedDto> = emptyList(),
    val author: List<IkiruNamedDto> = emptyList(),
    val artist: List<IkiruNamedDto> = emptyList(),
)

@Serializable
class IkiruNamedDto(
    val name: String? = null,
)

@Serializable
class IkiruChapterListDto(
    val chapters: List<IkiruChapterItemDto> = emptyList(),
    val hasMore: Boolean = false,
)

@Serializable
class IkiruChapterItemDto(
    private val title: String? = null,
    private val number: Float,
    private val createdAt: String? = null,
) {
    // the API no longer sends a chapter slug; the site's reader route is chapter-<number>
    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        this.url = "/manga/$mangaSlug/chapter-${formatChapterNumber(number)}"
        this.name = cleanChapterName(title, number)
        this.chapter_number = number
        this.date_upload = Instant.tryParse(createdAt)
    }

    private fun cleanChapterName(rawTitle: String?, num: Float?): String {
        val match = chapterTitleRegex.findAll(rawTitle.orEmpty()).lastOrNull()
        if (match != null) {
            val matched = match.value
            return if (matched.startsWith("chapter", ignoreCase = true)) {
                "Chapter${matched.substring(7)}"
            } else {
                matched
            }
        }
        if (num != null && num >= 0) {
            return "Chapter ${formatChapterNumber(num)}"
        }
        return rawTitle?.takeIf { it.isNotBlank() } ?: "Chapter"
    }

    private fun formatChapterNumber(number: Float): String = if (number % 1 == 0f) number.toInt().toString() else number.toString()

    companion object {
        private val chapterTitleRegex = Regex("(Chapter\\s*[\\d.]+(?:\\s*[:\\-].*)?)", RegexOption.IGNORE_CASE)
    }
}
