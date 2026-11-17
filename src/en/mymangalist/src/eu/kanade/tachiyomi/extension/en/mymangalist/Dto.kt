package eu.kanade.tachiyomi.extension.en.mymangalist

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH)

@Serializable
class ChaptersDto(
    private val group: ChapterGroupDto,
    @SerialName("next_offset") val nextOffset: Int,
    @SerialName("has_more") val hasMore: Boolean,
) {
    fun toSChapterList() = group.items.map { it.toSChapter() }
}

@Serializable
class ChapterGroupDto(val items: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val label: String,
    private val href: String,
    @SerialName("published_date") private val publishedDate: String?,
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/$href"
        name = label
        date_upload = dateFormat.tryParseDate(publishedDate)
    }
}

@Serializable
class ReaderDto(private val pageUrls: List<String>) {
    fun toPageList() = pageUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
}

@Serializable
class SelectData(
    val label: String,
    val parameter: String,
    val options: List<Pair<String, String>>,
    val default: Int,
)
