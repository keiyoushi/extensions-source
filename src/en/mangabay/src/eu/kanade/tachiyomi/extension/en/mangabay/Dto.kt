package eu.kanade.tachiyomi.extension.en.mangabay

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter

@Serializable
class ChapterListDto(
    @SerialName("news_id") private val newsId: Int,
    private val chapters: List<ChapterDto>,
) {
    fun toSChapterList(): List<SChapter> = chapters.map { it.toSChapter(newsId) }
}

@Serializable
class ChapterDto(
    private val id: Long,
    private val title: String,
    private val date: String = "",
) {
    fun toSChapter(newsId: Int): SChapter = SChapter.create().apply {
        url = "/reader/$newsId/$id"
        name = title
        date_upload = dateFormat.tryParseDate(date)
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d.M.yyyy")

@Serializable
class PageListDto(
    val images: List<String>,
)

@Serializable
class XFilters(
    @SerialName("filter_items") private val filterItems: XFilterItems,
) {
    val genres get() = filterItems.genre.values
}

@Serializable
class XFilterItems(
    @SerialName("g") val genre: XFilterItem,
)

@Serializable
class XFilterItem(
    val values: List<FilterValue>,
)

@Serializable
class FilterValue(
    val id: Int,
    val value: String,
)
