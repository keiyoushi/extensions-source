package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class IkiruResponseDto<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
    val code: Int? = null,
)

@Serializable
class IkiruSearchDataDto(
    val mangas: List<IkiruMangaItemDto> = emptyList(),
)

@Serializable
class IkiruMangaItemDto(
    val id: String,
    val title: String,
    val slug: String,
    val featuredImage: String? = null,
    val type: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        this.title = this@IkiruMangaItemDto.title
        this.url = this@IkiruMangaItemDto.slug
        this.thumbnail_url = featuredImage
    }
}

@Serializable
class IkiruMangaDetailDto(
    val id: String,
    val title: String,
    val slug: String,
    val description: String? = null,
    val status: String? = null,
    val featuredImage: String? = null,
    val metadata: IkiruMetadataDto? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        this.title = this@IkiruMangaDetailDto.title
        this.url = this@IkiruMangaDetailDto.slug
        this.thumbnail_url = featuredImage
        this.description = description?.let { Jsoup.parseBodyFragment(it).text() }
        this.status = when (this@IkiruMangaDetailDto.status?.uppercase()) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        this.author = metadata?.author?.mapNotNull { it.name }?.joinToString(", ")
        this.artist = metadata?.artist?.mapNotNull { it.name }?.joinToString(", ")
        this.genre = metadata?.genre?.mapNotNull { it.name }?.joinToString(", ")
    }
}

@Serializable
class IkiruMetadataDto(
    val genre: List<IkiruNamedDto> = emptyList(),
    val author: List<IkiruNamedDto> = emptyList(),
    val artist: List<IkiruNamedDto> = emptyList(),
)

@Serializable
class IkiruNamedDto(
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
class IkiruChapterListDto(
    val chapters: List<IkiruChapterItemDto> = emptyList(),
    val hasMore: Boolean = false,
    val lastIndex: String? = null,
    val count: Int? = null,
)

@Serializable
class IkiruChapterItemDto(
    val id: String,
    val slug: String,
    val title: String? = null,
    val number: Float? = null,
    val createdAt: String? = null,
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
        this.date_upload = parseIsoDate(createdAt)
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

    private fun parseIsoDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            format.parse(dateStr)?.time ?: 0L
        }.getOrDefault(0L)
    }

    companion object {
        private val chapterRegex = Regex("chapter-[\\d.]+(?:-\\w+)?", RegexOption.IGNORE_CASE)
        private val chapterTitleRegex = Regex("(Chapter\\s*[\\d.]+(?:\\s*[:\\-].*)?)", RegexOption.IGNORE_CASE)
    }
}
