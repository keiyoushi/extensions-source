package eu.kanade.tachiyomi.multisrc.mangadventure

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Generic results wrapper schema. */
@Serializable
internal class Results<T>(
    private val results: List<T>,
) : Iterable<T> by results

/** Generic paginator schema. */
@Serializable
internal class Paginator<T>(
    val last: Boolean,
    private val results: List<T>,
) : Iterable<T> by results

/** Page model schema. */
@Serializable
internal data class MAPage(
    private val id: Int,
    val image: String,
    val number: Int,
    val url: String,
) {
    override fun equals(other: Any?) =
        this === other || other is MAPage && id == other.id

    override fun hashCode() = id
}

/** Chapter model schema. */
@Serializable
internal data class Chapter(
    val id: Int,
    val title: String,
    val number: Float,
    val volume: Int?,
    val published: String,
    val final: Boolean,
    val series: String,
    val groups: List<String>,
    @SerialName("full_title") val fullTitle: String,
) {
    override fun equals(other: Any?) =
        this === other || other is Chapter && id == other.id

    override fun hashCode() = id
}

/** Series model schema. */
@Serializable
internal data class Series(
    val slug: String,
    val title: String,
    val cover: String,
    val description: String? = null,
    val status: String? = null,
    val licensed: Boolean? = null,
    val aliases: List<String>? = null,
    val authors: List<String>? = null,
    val artists: List<String>? = null,
    val categories: List<String>? = null,
) {
    override fun equals(other: Any?) =
        this === other || other is Series && slug == other.slug

    override fun hashCode() = slug.hashCode()
}
