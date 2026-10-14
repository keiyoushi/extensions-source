package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.time.Instant

@Serializable
class IkiruResponseDto<T>(
    val data: T? = null,
)

@Serializable
class IkiruSearchDataDto(
    val mangas: List<IkiruMangaItemDto> = emptyList(),
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

@Serializable
class IkiruMangaDetailDto(
    private val title: String,
    private val slug: String,
    private val description: String? = null,
    private val status: String? = null,
    private val featuredImage: String? = null,
    private val metadata: IkiruMetadataDto? = null,
) {
    fun toSManga(manga: SManga = SManga.create()): SManga = manga.apply {
        title = this@IkiruMangaDetailDto.title
        url = slug
        featuredImage?.let { thumbnail_url = it }

        val descHtml = this@IkiruMangaDetailDto.description
        val mainDescription = if (!descHtml.isNullOrBlank()) {
            val document = Jsoup.parseBodyFragment(descHtml)
            val paragraphs = document.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
            if (paragraphs.isNotEmpty()) {
                paragraphs.joinToString("\n\n")
            } else {
                document.text().trim()
            }
        } else {
            ""
        }

        val altTitles = metadata?.alternateTitles?.filter { it.isNotBlank() }.orEmpty()

        description = buildString {
            if (mainDescription.isNotBlank()) {
                append(mainDescription)
            }
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative Names:\n")
                append(altTitles.joinToString("\n") { "- $it" })
            }
        }.trim().ifEmpty { null }

        status = when (this@IkiruMangaDetailDto.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = metadata?.author?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ")
        artist = metadata?.artist?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ")
        genre = metadata?.genre?.mapNotNull { it.name }?.filter { it.isNotBlank() }?.joinToString(", ")
    }
}

@Serializable
class IkiruMetadataDto(
    val genre: List<IkiruNamedDto> = emptyList(),
    val author: List<IkiruNamedDto> = emptyList(),
    val artist: List<IkiruNamedDto> = emptyList(),
    val alternateTitles: List<String> = emptyList(),
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
    private val slug: String,
    private val title: String? = null,
    private val number: Float? = null,
    private val createdAt: String? = null,
) {
    fun toSChapter(mangaSlug: String): SChapter = SChapter.create().apply {
        val chapterSegment = chapterRegex.find(slug)?.value
            ?: (
                if (number != null && number >= 0) {
                    val numStr = if (number % 1 == 0f) number.toInt().toString() else number.toString()
                    "chapter-$numStr"
                } else {
                    slug.removePrefix("$mangaSlug-")
                }
                )

        this.url = "/manga/$mangaSlug/$chapterSegment"
        this.name = cleanChapterName(title, number)
        this.chapter_number = number ?: -1f
        this.date_upload = Instant.tryParse(createdAt)
    }

    private fun cleanChapterName(rawTitle: String?, num: Float?): String {
        val match = chapterTitleRegex.find(rawTitle.orEmpty())
        if (match != null) {
            val matched = match.value
            return if (matched.startsWith("chapter", ignoreCase = true)) {
                "Chapter" + matched.substring(7)
            } else {
                matched
            }
        }
        if (num != null && num >= 0) {
            val numStr = if (num % 1 == 0f) num.toInt().toString() else num.toString()
            return "Chapter $numStr"
        }
        return rawTitle?.takeIf { it.isNotBlank() } ?: "Chapter"
    }

    companion object {
        private val chapterRegex = Regex("chapter-[\\d.]+(?:-\\w+)?", RegexOption.IGNORE_CASE)
        private val chapterTitleRegex = Regex("(Chapter\\s*[\\d.]+(?:\\s*[:\\-].*)?)", RegexOption.IGNORE_CASE)
    }
}
