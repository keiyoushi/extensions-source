package eu.kanade.tachiyomi.extension.en.webcomics

import kotlinx.serialization.Serializable

@Serializable
data class ChapterWrapper(
    val `data`: Data,
) {
    val chapters get() = data.list
    val manga get() = data.book
}

@Serializable
data class Data(
    val list: List<ChapterDto>,
    val book: Book,
)

@Serializable
data class Book(
    val manga_id: String,
    val name: String,
)

@Serializable
data class ChapterDto(
    val chapter_id: String,
    val index: Int,
    val is_last: Boolean,
    val is_paid: Boolean,
    val is_pay: Boolean,
    val name: String,
    val update_time: Long,
)
