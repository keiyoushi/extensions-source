package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class Data<T>(val data: Result<T>)

@Serializable
class MultiData<T, V>(val data: MultiResult<T, V>)

@Serializable
class Result<T>(val result: T)

@Serializable
class MultiResult<T, V>(val result1: T, val result2: V)

@Serializable
data class Item(val id: String, val name: String)

@Serializable
data class Comic(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val imageUrl: String,
    var authors: List<Item>,
    val categories: List<Item>,
) {
    fun toSManga() = SManga.create().apply {
        url = "/comic/$id"
        title = this@Comic.title
        thumbnail_url = this@Comic.imageUrl
        author = this@Comic.authors.joinToString { it.name }
        genre = this@Comic.categories.joinToString { it.name }
        description = this@Comic.description
        status = when (this@Comic.status) {
            "ONGOING" -> SManga.ONGOING
            "END" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = this@Comic.description.isNotEmpty()
    }
}

@Serializable
data class Chapter(
    val id: String,
    val serial: String,
    val type: String,
    val size: Int,
    val dateCreated: String,
) {
    fun toSChapter(comicUrl: String, parseDate: (String) -> Long) = SChapter.create().apply {
        url = "$comicUrl/chapter/${this@Chapter.id}"
        name = when (this@Chapter.type) {
            "chapter" -> "第 ${this@Chapter.serial} 話"
            "book" -> "第 ${this@Chapter.serial} 卷"
            else -> this@Chapter.serial
        }
        scanlator = "${this@Chapter.size}P"
        date_upload = parseDate(this@Chapter.dateCreated)
        chapter_number = if (this@Chapter.type == "book") 0F else this@Chapter.serial.toFloatOrNull() ?: -1f
    }
}

@Serializable
data class Image(
    val id: String,
    val kid: String,
    val height: Int,
    val width: Int,
)
