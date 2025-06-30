package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class Data<T>(val data: Result<T>)

@Serializable
class Result<T>(val result: T)

@Serializable
data class ComicItem(val id: String, val name: String)

@Serializable
data class Comic(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val imageUrl: String,
    var authors: List<ComicItem>,
    val categories: List<ComicItem>,
) {
    private val parseStatus = when (status) {
        "ONGOING" -> SManga.ONGOING
        "END" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    fun toSManga() = SManga.create().apply {
        url = "/comic/$id"
        title = this@Comic.title
        thumbnail_url = this@Comic.imageUrl
        author = this@Comic.authors.joinToString(" ") { it.name }
        genre = this@Comic.categories.joinToString { it.name }
        description = this@Comic.description
        status = parseStatus
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
)

@Serializable
data class Image(
    val id: String,
    val kid: String,
    val height: Int,
    val width: Int,
)
