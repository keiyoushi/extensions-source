package eu.kanade.tachiyomi.extension.zh.manwa

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ImageSourceInfo(
    val name: String,
    val param: String,
)

@Serializable
class LatestUpdatesDto(
    val books: List<BookDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
class BookDto(
    @SerialName("book_name") private val bookName: String,
    private val id: Int,
    @SerialName("cover_url") private val coverUrl: String,
) {
    fun toSManga(imgHost: String) = SManga.create().apply {
        title = bookName
        url = "/book/$id"
        thumbnail_url = imgHost + coverUrl
    }
}
