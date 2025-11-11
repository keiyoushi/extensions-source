package eu.kanade.tachiyomi.extension.id.westmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class PaginatedData<T>(
    val data: List<T>,
    val paginator: Paginator,
)

@Serializable
class Paginator(
    @SerialName("current_page") private val current: Int,
    @SerialName("last_page") private val last: Int,
) {
    fun hasNextPage() = current < last
}

@Serializable
class BrowseManga(
    val title: String,
    val slug: String,
    val cover: String? = null,
)

@Serializable
class Manga(
    val title: String,
    val slug: String,
    @SerialName("alternative_name") val alternativeName: String? = null,
    @SerialName("sinopsis") val synopsis: String? = null,
    val cover: String? = null,
    val author: String? = null,
    @SerialName("country_id") val country: String? = null,
    val status: String? = null,
    val color: Boolean? = null,
    val genres: List<Genre>,
    val chapters: List<Chapter>,
)

@Serializable
class Genre(
    val name: String,
)

@Serializable
class Chapter(
    val slug: String,
    val number: String,
    @SerialName("updated_at") val updatedAt: Time,
)

@Serializable
class Time(
    val time: Long,
)

@Serializable
class ImageList(
    val images: List<String>,
)
