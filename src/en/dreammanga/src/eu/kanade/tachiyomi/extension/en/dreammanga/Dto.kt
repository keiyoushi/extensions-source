package eu.kanade.tachiyomi.extension.en.dreammanga

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class MangaDataDto(
    @SerialName("news_id") val newsId: Int,
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    private val id: Long,
    private val title: String,
    private val date: String? = null,
) {
    fun toSChapter(newsId: Int) = SChapter.create().apply {
        url = "/reader/$newsId/$id"
        name = title.replace(SPACES, " ").trim()
        chapter_number = CHAPTER_NUMBER.find(title)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
        date_upload = DATE_FORMAT.tryParseDate(date)
    }
}

@Serializable
class ReaderDto(
    val images: List<String> = emptyList(),
    val pages: Int? = null,
    val age: Int? = null,
    @SerialName("post_link") private val postLink: String? = null,
    private val link: String? = null,
) {
    // The warning page of an adult manga has its link under another name
    val mangaLink get() = postLink ?: link
}

@Serializable
class XFilterDto(
    @SerialName("filter_items") val filterItems: FilterItemsDto,
)

@Serializable
class FilterItemsDto(
    val g: GenreGroupDto,
)

@Serializable
class GenreGroupDto(
    val values: List<GenreDto>,
)

@Serializable
class GenreDto(
    val id: Int,
    val value: String,
)

private val SPACES = Regex("""\s+""")
private val CHAPTER_NUMBER = Regex("""Chapter\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)
