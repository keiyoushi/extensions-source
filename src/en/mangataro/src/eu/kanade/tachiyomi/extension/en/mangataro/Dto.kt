package eu.kanade.tachiyomi.extension.en.mangataro

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchPayload(
    private val page: Int,
    private val search: String,
    private val years: String,
    private val genres: String,
    private val types: String,
    private val statuses: String,
    private val sort: String,
    private val genreMatchMode: String,
)

@Serializable
class BrowseManga(
    val id: String,
    val url: String,
    val title: String,
    val cover: String,
    val type: String,
    val description: String,
    val status: String,
)

@Serializable
class MangaUrl(
    val id: String,
    val slug: String,
)

@Serializable
class MangaDetails(
    val id: Int,
    val slug: String,
    val title: Rendered,
    val content: Rendered,
    @SerialName("featured_media")
    val featuredMedia: Int,
    @SerialName("class_list")
    private val classList: List<String>,
) {
    fun getFromClassList(type: String): List<String> {
        return classList.filter { it.startsWith("$type-") }
            .map {
                it.substringAfter("$type-")
                    .split("-")
                    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }
            }
    }
}

@Serializable
class Thumbnail(
    @SerialName("source_url")
    val url: String,
)

@Serializable
class Rendered(
    val rendered: String,
)
