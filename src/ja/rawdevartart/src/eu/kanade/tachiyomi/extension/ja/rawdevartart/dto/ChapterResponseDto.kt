package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class ChapterDetailDto(
    @SerialName("chapter_id") private val id: String,
    @SerialName("chapter_title") private val title: String,
    @SerialName("chapter_number") private val number: Float,
    @SerialName("chapter_date_published") private val datePublished: String,
    @SerialName("chapter_content") val content: String? = null,
    val server: String,
)

@Serializable
class ChapterResponseDto(
    @SerialName("chapter_detail") private val detail: ChapterDetailDto,
) {
    fun toPageList(): List<Page> {
        val document = Jsoup.parseBodyFragment(detail.content!!, detail.server)

        return document.select("img[data-src]").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("data-src"))
        }
    }
}
