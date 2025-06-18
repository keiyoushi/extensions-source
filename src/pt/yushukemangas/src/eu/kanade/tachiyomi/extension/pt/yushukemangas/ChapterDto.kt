package eu.kanade.tachiyomi.extension.pt.yushukemangas

import eu.kanade.tachiyomi.extension.pt.yushukemangas.YushukeMangas.Companion.dateFormat
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(
    val id: Int,
    @SerialName("numero")
    val number: Float,
    @SerialName("titulo")
    val name: String,
    @SerialName("data_publicacao")
    val date: String,
) {
    fun toSChapter(chapterUrl: String) = SChapter.create().apply {
        name = this@ChapterDto.name
        chapter_number = number
        date_upload = dateFormat.tryParse(date)
        url = "$chapterUrl/capitulo/$id"
    }
}
