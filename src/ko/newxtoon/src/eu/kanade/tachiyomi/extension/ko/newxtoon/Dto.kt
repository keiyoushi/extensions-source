package eu.kanade.tachiyomi.extension.ko.newxtoon

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class ChapterListDto(
    val chapters: List<ChapterDto>,
    @SerialName("has_more") val hasMore: Boolean,
)

@Serializable
class ChapterDto(
    private val id: Long,
    private val title: String,
    private val date: String? = null,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "/comics/$mangaId/chapters/$id"
        name = title
        date_upload = dateFormat.tryParseDate(date, seoul)
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val seoul = ZoneId.of("Asia/Seoul")
