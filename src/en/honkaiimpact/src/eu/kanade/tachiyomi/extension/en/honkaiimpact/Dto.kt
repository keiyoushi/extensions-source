package eu.kanade.tachiyomi.extension.en.honkaiimpact

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

@Serializable
class Dto(
    private val title: String,
    @SerialName("bookid") private val bookId: Int,
    @SerialName("chapterid") private val chapterId: Float,
    private val timestamp: String,
) {
    fun toSChapter() = SChapter.create().apply {
        name = title
        url = "/book/$bookId/${chapterId.toInt()}"
        date_upload = dateFormat.tryParse(timestamp)
        chapter_number = chapterId
    }
}
