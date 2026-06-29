package eu.kanade.tachiyomi.extension.en.valirscans

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class BookSchema(
    @SerialName("@type") private val type: String? = null,
    val name: String,
    private val author: AuthorSchema? = null,
    val image: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
) {
    val authorName: String? get() = author?.name
    fun isBook() = type == "Book"
}

@Serializable
class AuthorSchema(
    val name: String? = null,
)

@Serializable
class SeriesDetailsDto(
    val title: String,
    val slug: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val coverImage: String? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class SeriesPageDto(
    val series: SeriesDetailsDto,
    val chapters: List<ChapterDto> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val number: Float,
    private val title: String? = null,
    val isLocked: Boolean = false,
    private val publishedAt: String? = null,
) {
    fun toSChapter(seriesPath: String, dateFormat: SimpleDateFormat): SChapter {
        val chapterNumberStr = number.toString().removeSuffix(".0")
        return SChapter.create().apply {
            url = "$seriesPath/chapter/$chapterNumberStr"
            name = buildString {
                if (isLocked) append("🔒 ")
                append(title?.ifEmpty { "Chapter $chapterNumberStr" } ?: "Chapter $chapterNumberStr")
            }
            chapter_number = number
            date_upload = dateFormat.tryParse(publishedAt)
        }
    }
}

@Serializable
class ReaderChapterDto(
    val pages: List<ReaderPageDto> = emptyList(),
)

@Serializable
class ChapterPageDto(
    val chapter: ReaderChapterDto? = null,
)

@Serializable
class ReaderPageDto(
    val pageNumber: Int,
    val imageUrl: String,
)
