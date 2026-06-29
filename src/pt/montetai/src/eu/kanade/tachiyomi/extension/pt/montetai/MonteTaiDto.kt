package eu.kanade.tachiyomi.extension.pt.montetai

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    val `data`: Wrapper,
) {
    fun toSChapterList(parseChapterDate: (date: String?) -> Long): List<SChapter> = data.rows.map {
        SChapter.create().apply {
            name = it.title
            url = it.url
            date_upload = parseChapterDate(it.date)
        }
    }
}

@Serializable
class Wrapper(
    val rows: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val url: String,
    val title: String,
    @SerialName("meta")
    val date: String,
)
