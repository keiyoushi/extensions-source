package eu.kanade.tachiyomi.extension.en.webtoonclub

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ChaptersDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val chapterNumber: String,
    private val slug: String,
    private val title: String?,
    private val date: String?,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        // The site's chapter links and labels use the slug's number: "chapter-4.1" for 4.10
        val number = slug.removePrefix("chapter-")
        url = "$mangaUrl/chapter/$number"
        name = listOfNotNull("Chapter $number", title).joinToString(": ")
        chapter_number = chapterNumber.toFloat()
        date_upload = Instant.tryParse(date)
    }
}

@Serializable
class ReaderChapterDto(
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val filename: String,
    val sortOrder: Int,
)

@Serializable
class LdGraphDto(
    @SerialName("@graph") val graph: List<LdBookDto>,
)

@Serializable
class LdBookDto(
    @SerialName("@type") val type: String,
    val image: String? = null,
    val description: String? = null,
    val alternateName: List<String> = emptyList(),
    val author: List<LdNameDto> = emptyList(),
    val character: List<LdNameDto> = emptyList(),
    val genre: List<String> = emptyList(),
)

@Serializable
class LdNameDto(
    val name: String,
)

@Serializable
class RatingDto(
    val ratingAvg: Float,
    val ratingCount: Int,
)
