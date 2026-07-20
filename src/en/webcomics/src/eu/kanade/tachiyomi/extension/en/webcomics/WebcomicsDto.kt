package eu.kanade.tachiyomi.extension.en.webcomics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GenreListItem(
    @SerialName("manga_id") val mangaId: String,
    val cover: String,
    val name: String,
)

@Serializable
class ChapterWrapper(
    val `data`: Data,
) {
    val chapters get() = data.list
    val manga get() = data.book
}

@Serializable
class Data(
    val list: List<ChapterDto>,
    val book: Book,
)

@Serializable
class Book(
    val manga_id: String,
    val name: String,
)

@Serializable
class ChapterDto(
    val chapter_id: String,
    val index: Int,
    val is_last: Boolean,
    val is_paid: Boolean,
    val is_pay: Boolean,
    val name: String,
    val update_time: Long,
)

@Serializable
class PageDto(
    val src: String,
)

@Serializable
class UserAgentList(
    val desktop: List<String>,
)
